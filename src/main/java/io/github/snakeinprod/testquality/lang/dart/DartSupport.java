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
    walk(file.root(), "", false, DartTrees.imports(file.root(), "bloc_test"), mockClasses(file.root()), testFunctions(file), out);
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  /**
   * Also the calls of wrappers declared in the project files the file imports, or in the files those export (one
   * level). A wrapper of the file itself wins over an imported one of the same name.
   */
  @Override
  public TestFile extract(ParsedFile file, Set<String> paths, Function<String, String> read) {
    Map<String, TestFunction> imported = new HashMap<>();
    for (String p : directives(file, paths, read, Set.of("import"))) {
      Module m = module(p, paths, read);
      m.wrappers().forEach(imported::putIfAbsent);
      for (String e : m.exports()) {
        module(e, paths, read).wrappers().forEach(imported::putIfAbsent);
      }
    }
    if (!imported.isEmpty()) {
      file.put(Imported.class, new Imported(imported));
    }
    return extract(file);
  }

  /** The wrappers a test file imports, attached to it by {@link #extract(ParsedFile, Set, Function)}. */
  private record Imported(Map<String, TestFunction> functions) {
  }

  /** The wrappers a project file declares and the project files it exports. */
  private record Module(Map<String, TestFunction> wrappers, List<String> exports) {
  }

  private static final Module NO_MODULE = new Module(Map.of(), List.of());
  /** A file without a framework test call or an export declares and exports no wrapper: it is not parsed. */
  private static final Pattern WRAPPER_HINT = Pattern.compile("\\b(test|testWidgets|testGoldens)\\s*\\(|\\bexport\\s");
  private final Map<String, Module> modules = new HashMap<>();

  /** A project file's wrappers and exports, indexed once per analysis. */
  private Module module(String path, Set<String> paths, Function<String, String> read) {
    Module m = modules.get(path);
    if (m == null) {
      String source = read.apply(path);
      m = NO_MODULE;
      if (source != null && WRAPPER_HINT.matcher(source).find()) {
        try {
          ParsedFile f = parse(path, "dart", source);
          Map<String, TestFunction> wrappers = new HashMap<>(testFunctions(f.root()));
          wrappers.values().removeIf(w -> w.inner() == null);
          m = new Module(wrappers, directives(f, paths, read, Set.of("export")));
        } catch (RuntimeException e) {
          // an unparsable file declares no wrapper
        }
      }
      modules.put(path, m);
    }
    return m;
  }

  /**
   * {@code import}, {@code export} and {@code part} directives: relative URIs from the file's directory,
   * {@code package:<name>/x.dart} as {@code lib/x.dart} of the project package whose pubspec.yaml has that name.
   */
  @Override
  public Outline outline(ParsedFile file, Set<String> paths, Function<String, String> read) {
    Set<String> imports = new TreeSet<>(directives(file, paths, read, Set.of("import", "export", "part")));
    List<Token> functions = new ArrayList<>();
    List<Token> classes = new ArrayList<>();
    declarations(file.root(), functions, classes);
    return new Outline(imports, functions, classes);
  }

  /** The project files the directives of these kinds name, see {@link #outline}. */
  private List<String> directives(ParsedFile file, Set<String> paths, Function<String, String> read, Set<String> kinds) {
    List<String> out = new ArrayList<>();
    List<Token> t = file.tokens();
    for (int i = 1; i < t.size(); i++) {
      Token p = t.get(i - 1);
      if (t.get(i).kind() == TokenKind.STRING && p.kind() == TokenKind.IDENT && kinds.contains(p.text())) {
        String uri = DartTrees.stringValue(t.get(i));
        String r;
        if (uri.startsWith("package:") && uri.indexOf('/') > 0) {
          String root = packages(paths, read).get(uri.substring(8, uri.indexOf('/')));
          r = root == null ? null : root + "lib/" + uri.substring(uri.indexOf('/') + 1);
        } else {
          r = uri.contains(":") ? null : Outline.join(Outline.dir(file.path()), uri);
        }
        if (r != null && paths.contains(r) && !r.equals(file.path())) {
          out.add(r);
        }
      }
    }
    return out;
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

  private static void walk(Node.Group g, String suite, boolean skipped, boolean blocTests, Set<String> mockClasses,
    Map<String, TestFunction> fns, List<TestCase> out) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      Token t = Nodes.leaf(n);
      boolean bloc = blocTests && t != null && t.isIdent("blocTest");
      int a = bloc ? afterTypeArgs(kids, i + 1) : i + 1;
      TestFunction fn = t == null || t.kind() != TokenKind.IDENT ? null : fns.get(t.text());
      if (t != null && t.kind() == TokenKind.IDENT && (fn != null || t.is("group") || bloc) && isCall(kids, i, a)) {
        Node.Group args = (Node.Group) kids.get(a);
        if (fn != null && fns.values().stream().anyMatch(w -> t == w.inner())) {
          // the wrapper's own call: the wrapper's callers are the tests
          i = a;
          continue;
        }
        List<List<Node>> positional = DartTrees.positionalArgs(args);
        int nameAt = fn == null ? 0 : fn.name();
        if (positional.size() <= nameAt || fn != null && fn.inner() != null && !literal(positional.get(nameAt))) {
          continue;
        }
        Token nameToken = nameToken(positional.get(nameAt));
        String name = name(positional.get(nameAt));
        if (bloc) {
          Map<String, List<Node>> named = DartTrees.namedArgs(args);
          int expects = (named.containsKey("expect") ? 1 : 0) + (named.containsKey("errors") ? 1 : 0);
          out.add(new TestCase(name, suite, nameToken, t, args.last(), args, skipped, true, expects + assertions(args),
            mocks(args, mockClasses)));
          i = a;
          continue;
        }
        boolean skip = skipped || isSkip(args);
        int bodyAt = fn == null ? 1 : fn.body();
        DartTrees.Closure closure = positional.size() > bodyAt ? DartTrees.closure(positional.get(bodyAt)) : null;
        if (fn == null) {
          walk(args, suite.isEmpty() ? name : suite + " > " + name, skip, blocTests, mockClasses, fns, out);
        } else {
          Node.Group body = closure == null ? null : closure.body();
          out.add(new TestCase(name, suite, nameToken, t, args.last(), body, skip, closure != null && closure.async(),
            body == null ? 0 : assertions(body), body == null ? 0 : mocks(body, mockClasses)));
        }
        i = a;
      } else if (n instanceof Node.Group sub) {
        walk(sub, suite, skipped, blocTests, mockClasses, fns, out);
      }
    }
  }

  /**
   * A function that runs a test: {@code test}, {@code testWidgets}, {@code testGoldens}, or a function of the same file
   * that passes its own parameters to one of them as name and body, such as {@code void slideshowTest(String name,
   * Future<void> Function(WidgetTester) body) { testWidgets(name, (t) async { await body(t); }); }}. Each call of such a
   * wrapper with a literal name is a test; its own call of the framework function is not.
   *
   * @param function the framework function it calls
   * @param name     index of the positional argument holding the test name
   * @param body     index of the positional argument holding the test body
   * @param tester   index of the body's parameter that gets the WidgetTester, -1 when unknown
   * @param inner    the wrapper's call of the framework function, null for the framework functions; compared by identity,
   *                 so a wrapper imported from another file never matches a token of this one
   */
  public record TestFunction(String function, int name, int body, int tester, Token inner) {
  }

  /** The framework test functions and the wrappers declared in the file or imported into it, by name. */
  public static Map<String, TestFunction> testFunctions(ParsedFile file) {
    Map<String, TestFunction> out = testFunctions(file.root());
    Imported imported = file.get(Imported.class);
    if (imported != null) {
      imported.functions().forEach(out::putIfAbsent);
    }
    return out;
  }

  /** The framework test functions and the wrappers declared in the file, by name. */
  public static Map<String, TestFunction> testFunctions(Node.Group root) {
    Map<String, TestFunction> out = new HashMap<>();
    for (String f : TEST_FUNCTIONS) {
      out.put(f, new TestFunction(f, 0, 1, 0, null));
    }
    Nodes.groups(root, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token t = Nodes.leaf(kids.get(i));
        Token prev = i > 0 ? Nodes.leaf(kids.get(i - 1)) : null;
        if (t != null && t.kind() == TokenKind.IDENT && !TEST_FUNCTIONS.contains(t.text()) && !Chains.keywords("dart").contains(t.text())
          && (prev == null || !ACCESSORS.contains(prev.text()))) {
          DartTrees.Closure decl = DartTrees.closureAt(kids, i + 1);
          TestFunction w = decl == null ? null : wrapper(decl);
          if (w != null) {
            out.put(t.text(), w);
          }
        }
      }
    });
    return out;
  }

  /**
   * The test function a declaration wraps: it calls one with a parameter as the name, and as the body either another
   * parameter or a closure that calls that parameter.
   */
  private static TestFunction wrapper(DartTrees.Closure decl) {
    List<String> params = new ArrayList<>();
    for (List<Node> item : decl.params().items()) {
      if (item.get(0) instanceof Node.Group) {
        break; // {named} or [optional] parameters
      }
      params.add(Nodes.text(item.get(item.size() - 1)));
    }
    for (Chain c : Chains.in(decl.body(), Chains.keywords("dart"))) {
      Chain.Call call = c.callAfter(0);
      if (call == null || c.segments().size() != 2 || !TEST_FUNCTIONS.contains(c.firstName())) {
        continue;
      }
      List<List<Node>> pos = DartTrees.positionalArgs(call.args());
      int name = pos.size() < 2 || pos.get(0).size() != 1 ? -1 : params.indexOf(Nodes.text(pos.get(0)));
      if (name < 0) {
        continue;
      }
      int direct = pos.get(1).size() == 1 ? params.indexOf(Nodes.text(pos.get(1))) : -1;
      if (direct >= 0 && direct != name) {
        return new TestFunction(c.firstName(), name, direct, 0, c.first());
      }
      DartTrees.Closure inner = DartTrees.closure(pos.get(1));
      if (inner == null) {
        continue;
      }
      List<List<Node>> innerParams = inner.params().items();
      String tester = innerParams.isEmpty() ? null : Nodes.text(innerParams.get(0).get(innerParams.get(0).size() - 1));
      for (Chain b : Chains.in(inner.body(), Chains.keywords("dart"))) {
        int body = params.indexOf(b.firstName());
        if (body >= 0 && body != name && b.callAfter(0) != null) {
          List<List<Node>> bodyArgs = DartTrees.positionalArgs(b.callAfter(0).args());
          int at = -1;
          for (int k = 0; k < bodyArgs.size() && at < 0; k++) {
            at = bodyArgs.get(k).size() == 1 && Nodes.text(bodyArgs.get(k)).equals(tester) ? k : -1;
          }
          return new TestFunction(c.firstName(), name, body, at, c.first());
        }
      }
    }
    return null;
  }

  /** The test is a call of {@code function}, directly or through a wrapper. */
  public static boolean calls(Map<String, TestFunction> fns, TestCase t, String function) {
    TestFunction f = fns.get(t.start().text());
    return f != null && f.function().equals(function);
  }

  private static boolean literal(List<Node> arg) {
    Token t = Nodes.leaf(arg.get(0));
    return t != null && t.kind() == TokenKind.STRING;
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
        // primary constructor: class MockRepo() extends Mock
        if (j < kids.size() && kids.get(j) instanceof Node.Group params && params.is("(")) {
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
