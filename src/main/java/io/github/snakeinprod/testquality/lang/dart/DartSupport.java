package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.Outline;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and test extraction for Dart: {@code test}, {@code testWidgets} and {@code testGoldens} calls, inside
 * {@code group} calls for the suite, with the {@code skip:} named argument on tests and groups.
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
    walk(file.root(), "", false, mockClasses(file.root()), out);
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  /**
   * {@code import}, {@code export} and {@code part} directives: relative URIs from the file's directory,
   * {@code package:<name>/x.dart} as {@code lib/x.dart} of the project package whose pubspec.yaml has that name.
   */
  @Override
  public Outline outline(ParsedFile file, Set<String> paths, Function<String, String> read) {
    Set<String> imports = new TreeSet<>();
    List<Token> t = file.tokens();
    for (int i = 1; i < t.size(); i++) {
      Token p = t.get(i - 1);
      if (t.get(i).kind() == TokenKind.STRING && (p.isIdent("import") || p.isIdent("export") || p.isIdent("part"))) {
        String uri = DartTrees.stringValue(t.get(i));
        String r;
        if (uri.startsWith("package:") && uri.indexOf('/') > 0) {
          String root = packages(paths, read).get(uri.substring(8, uri.indexOf('/')));
          r = root == null ? null : root + "lib/" + uri.substring(uri.indexOf('/') + 1);
        } else {
          r = uri.contains(":") ? null : Outline.join(Outline.dir(file.path()), uri);
        }
        if (r != null && paths.contains(r) && !r.equals(file.path())) {
          imports.add(r);
        }
      }
    }
    List<Token> functions = new ArrayList<>();
    List<Token> classes = new ArrayList<>();
    declarations(file.root(), functions, classes);
    return new Outline(imports, functions, classes);
  }

  /** Package name to its directory ("" or "a/b/"): every directory holding a lib/ with dart files and a pubspec.yaml. */
  private Map<String, String> packages(Set<String> paths, Function<String, String> read) {
    if (packages == null) {
      packages = new HashMap<>();
      Set<String> roots = new TreeSet<>();
      for (String p : paths) {
        int lib = p.startsWith("lib/") ? 0 : p.indexOf("/lib/") + 1;
        if (lib > 0 || p.startsWith("lib/")) {
          roots.add(p.substring(0, lib));
        }
      }
      for (String root : roots) {
        String yaml = read.apply(root + "pubspec.yaml");
        Matcher m = yaml == null ? null : PUBSPEC_NAME.matcher(yaml);
        if (m != null && m.find()) {
          packages.putIfAbsent(m.group(1), root);
        }
      }
    }
    return packages;
  }

  private static final Pattern PUBSPEC_NAME = Pattern.compile("(?m)^name:\\s*['\"]?(\\w+)");
  private static final Set<String> TYPES = Set.of("class", "mixin", "extension", "enum");
  private Map<String, String> packages;

  /** Top-level functions and the methods of classes, mixins, extensions and enums (those with a body or "=>"). */
  private static void declarations(Node.Group g, List<Token> functions, List<Token> classes) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Token t = Nodes.leaf(kids.get(i));
      if (t == null || t.kind() != TokenKind.IDENT) {
        continue;
      }
      if (TYPES.contains(t.text())) {
        int j = i + 1;
        while (j < kids.size() && !(kids.get(j) instanceof Node.Group b && b.is("{")) && !Nodes.isLeaf(kids.get(j), ";")) {
          j++;
        }
        if (j < kids.size() && kids.get(j) instanceof Node.Group body) {
          Token name = Nodes.leaf(kids.get(i + 1));
          classes.add(name != null && name.kind() == TokenKind.IDENT ? name : t);
          declarations(body, functions, classes);
          i = j;
        }
        continue;
      }
      if (Chains.keywords("dart").contains(t.text())) {
        continue;
      }
      int j = i + 1;
      if (j < kids.size() && Nodes.isLeaf(kids.get(j), "<")) {
        // generic function: T first<T>(List<T> xs)
        while (j < kids.size() && !Nodes.isLeaf(kids.get(j), ">") && !Nodes.isLeaf(kids.get(j), ">>")) {
          j++;
        }
        j++;
      }
      Node next = j + 1 < kids.size() ? kids.get(j + 1) : null;
      if (j < kids.size() && kids.get(j) instanceof Node.Group params && params.is("(")
        && (next instanceof Node.Group b && b.is("{") || Nodes.isLeaf(next, "=>") || Nodes.isIdent(next, "async")
        || Nodes.isIdent(next, "sync"))) {
        functions.add(t);
        i = j;
      }
    }
  }

  private static void walk(Node.Group g, String suite, boolean skipped, Set<String> mockClasses, List<TestCase> out) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      Token t = Nodes.leaf(n);
      if (t != null && t.kind() == TokenKind.IDENT && (TEST_FUNCTIONS.contains(t.text()) || t.is("group")) && isCall(kids, i)) {
        Node.Group args = (Node.Group) kids.get(i + 1);
        List<List<Node>> positional = DartTrees.positionalArgs(args);
        if (positional.isEmpty()) {
          continue;
        }
        Token nameToken = nameToken(positional.get(0));
        String name = name(positional.get(0));
        boolean skip = skipped || isSkip(args);
        DartTrees.Closure closure = positional.size() > 1 ? DartTrees.closure(positional.get(1)) : null;
        if (t.is("group")) {
          walk(args, suite.isEmpty() ? name : suite + " > " + name, skip, mockClasses, out);
        } else {
          Node.Group body = closure == null ? null : closure.body();
          out.add(new TestCase(name, suite, nameToken, t, args.last(), body, skip, closure != null && closure.async(),
            body == null ? 0 : assertions(body), body == null ? 0 : mocks(body, mockClasses)));
        }
        i++;
      } else if (n instanceof Node.Group sub) {
        walk(sub, suite, skipped, mockClasses, out);
      }
    }
  }

  /** {@code name(...)} as a call: not a member ({@code x.test(...)}) and not a declaration ({@code void test() {}}). */
  private static boolean isCall(List<Node> kids, int i) {
    if (i + 1 >= kids.size() || !(kids.get(i + 1) instanceof Node.Group args) || !args.is("(")) {
      return false;
    }
    Token prev = i > 0 ? Nodes.leaf(kids.get(i - 1)) : null;
    if (prev != null && (ACCESSORS.contains(prev.text()) || prev.kind() == TokenKind.IDENT && !Chains.keywords("dart").contains(prev.text()))) {
      return false;
    }
    Node next = i + 2 < kids.size() ? kids.get(i + 2) : null;
    return !(next instanceof Node.Group body && body.is("{")) && !Nodes.isLeaf(next, "=>") && !Nodes.isIdent(next, "async");
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
