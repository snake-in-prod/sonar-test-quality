package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Lexed;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsing and test extraction for Dart: {@code test}, {@code testWidgets} and {@code testGoldens} calls, inside
 * {@code group} calls for the suite, with the {@code skip:} named argument on tests and groups. A {@code blocTest} from
 * package:bloc_test is a test too (in files importing it): its body is the call's argument group, its assertions {@code expect:} and
 * {@code errors:} (one each) plus the assertion calls in its arguments ({@code verify:}); its {@code skip:} is a count of
 * states, not a skip.
 */
public class DartSupport implements LanguageSupport {

  public static final Set<String> TEST_FUNCTIONS = Set.of("test", "testWidgets", "testGoldens");
  public static final Set<String> ASSERTIONS = Set.of("expect", "expectLater", "verify", "verifyNever", "verifyInOrder",
    "verifyZeroInteractions", "verifyNoMoreInteractions", "screenMatchesGolden", "multiScreenGolden");
  private static final Set<String> ACCESSORS = Set.of(".", "?.", "..", "?..", "!.");

  @Override
  public String family() {
    return "dart";
  }

  @Override
  public List<String> languages() {
    return List.of("dart");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    Lexed lexed = DartLexer.lex(source);
    return new ParsedFile(path, language, source, lexed.tokens(), lexed.comments());
  }

  @Override
  public TestFile extract(ParsedFile file) {
    List<TestCase> out = new ArrayList<>();
    walk(file.root(), "", false, DartTrees.imports(file.root(), "bloc_test"), mockClasses(file.root()), out);
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  private static void walk(Node.Group g, String suite, boolean skipped, boolean blocTests, Set<String> mockClasses, List<TestCase> out) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      Token t = Nodes.leaf(n);
      boolean bloc = blocTests && t != null && t.isIdent("blocTest");
      int a = bloc ? afterTypeArgs(kids, i + 1) : i + 1;
      if (t != null && t.kind() == TokenKind.IDENT && (TEST_FUNCTIONS.contains(t.text()) || t.is("group") || bloc) && isCall(kids, i, a)) {
        Node.Group args = (Node.Group) kids.get(a);
        List<List<Node>> positional = DartTrees.positionalArgs(args);
        if (positional.isEmpty()) {
          continue;
        }
        Token nameToken = nameToken(positional.get(0));
        String name = name(positional.get(0));
        if (bloc) {
          Map<String, List<Node>> named = DartTrees.namedArgs(args);
          int expects = (named.containsKey("expect") ? 1 : 0) + (named.containsKey("errors") ? 1 : 0);
          out.add(new TestCase(name, suite, nameToken, t, args.last(), args, skipped, true, expects + assertions(args),
            mocks(args, mockClasses)));
          i = a;
          continue;
        }
        boolean skip = skipped || isSkip(args);
        DartTrees.Closure closure = positional.size() > 1 ? DartTrees.closure(positional.get(1)) : null;
        if (t.is("group")) {
          walk(args, suite.isEmpty() ? name : suite + " > " + name, skip, blocTests, mockClasses, out);
        } else {
          Node.Group body = closure == null ? null : closure.body();
          out.add(new TestCase(name, suite, nameToken, t, args.last(), body, skip, closure != null && closure.async(),
            body == null ? 0 : assertions(body), body == null ? 0 : mocks(body, mockClasses)));
        }
        i = a;
      } else if (n instanceof Node.Group sub) {
        walk(sub, suite, skipped, blocTests, mockClasses, out);
      }
    }
  }

  /** {@code name(...)} as a call: not a member ({@code x.test(...)}) and not a declaration ({@code void test() {}}). */
  private static boolean isCall(List<Node> kids, int i, int a) {
    if (a >= kids.size() || !(kids.get(a) instanceof Node.Group args) || !args.is("(")) {
      return false;
    }
    Token prev = i > 0 ? Nodes.leaf(kids.get(i - 1)) : null;
    if (prev != null && (ACCESSORS.contains(prev.text()) || prev.kind() == TokenKind.IDENT && !Chains.keywords("dart").contains(prev.text()))) {
      return false;
    }
    Node next = a + 1 < kids.size() ? kids.get(a + 1) : null;
    return !(next instanceof Node.Group body && body.is("{")) && !Nodes.isLeaf(next, "=>") && !Nodes.isIdent(next, "async");
  }

  /** The index after type arguments {@code <A, List<B>>} starting at {@code i}, {@code i} when there are none. */
  private static int afterTypeArgs(List<Node> kids, int i) {
    if (i >= kids.size() || !Nodes.isLeaf(kids.get(i), "<")) {
      return i;
    }
    int depth = 0;
    for (int j = i; j < kids.size(); j++) {
      Token t = Nodes.leaf(kids.get(j));
      if (t == null) {
        continue;
      }
      depth += switch (t.text()) {
        case "<" -> 1;
        case ">" -> -1;
        case ">>" -> -2;
        case ">>>" -> -3;
        default -> 0;
      };
      if (depth <= 0 || t.is(";")) {
        return j + 1;
      }
    }
    return kids.size();
  }

  private static Token nameToken(List<Node> description) {
    for (Node n : description) {
      Token t = Nodes.leaf(n);
      if (t != null && t.kind() == TokenKind.STRING) {
        return t;
      }
    }
    return description.get(0).first();
  }

  /** Adjacent string literals joined; any other expression as its token text. */
  private static String name(List<Node> description) {
    StringBuilder sb = new StringBuilder();
    for (Node n : description) {
      Token t = Nodes.leaf(n);
      if (t == null || t.kind() != TokenKind.STRING) {
        return Nodes.text(description);
      }
      sb.append(DartTrees.stringValue(t));
    }
    return sb.toString();
  }

  /** {@code skip: true} or {@code skip: 'reason'}; a variable or {@code false} is not a skip. */
  private static boolean isSkip(Node.Group args) {
    List<Node> v = DartTrees.namedArgs(args).get("skip");
    if (v == null || v.isEmpty()) {
      return false;
    }
    Token t = Nodes.leaf(v.get(0));
    return t != null && (v.size() == 1 && t.isIdent("true") || t.kind() == TokenKind.STRING);
  }

  private static int assertions(Node.Group body) {
    int count = 0;
    for (Chain c : Chains.in(body)) {
      if (ASSERTIONS.contains(c.firstName()) && c.callAfter(0) != null) {
        count++;
      }
    }
    return count;
  }

  /** {@code when(...)}, {@code MockX()} and constructions of classes declared here that extend Mock or Fake. */
  private static int mocks(Node.Group body, Set<String> mockClasses) {
    int count = 0;
    for (Chain c : Chains.in(body)) {
      String first = c.firstName();
      if (c.callAfter(0) != null && (first.equals("when") || mockClasses.contains(first) || isMockName(first))) {
        count++;
      }
    }
    return count;
  }

  private static boolean isMockName(String s) {
    return s.length() > 4 && s.startsWith("Mock") && Character.isUpperCase(s.charAt(4));
  }

  /** Names of classes declared in the file that extend {@code Mock} or {@code Fake}. */
  static Set<String> mockClasses(Node.Group root) {
    Set<String> out = new HashSet<>();
    List<Node> kids = root.children();
    for (int i = 0; i + 3 < kids.size(); i++) {
      if (Nodes.isIdent(kids.get(i), "class") && Nodes.leaf(kids.get(i + 1)) != null) {
        int j = i + 2;
        // generic parameters: class MockRepo<T> extends Mock
        if (Nodes.isLeaf(kids.get(j), "<")) {
          while (j < kids.size() && !Nodes.isLeaf(kids.get(j), ">") && !Nodes.isLeaf(kids.get(j), ">>")) {
            j++;
          }
          j++;
        }
        if (j + 1 < kids.size() && Nodes.isIdent(kids.get(j), "extends")
          && (Nodes.isIdent(kids.get(j + 1), "Mock") || Nodes.isIdent(kids.get(j + 1), "Fake"))) {
          out.add(Nodes.leaf(kids.get(i + 1)).text());
        }
      }
    }
    return out;
  }
}
