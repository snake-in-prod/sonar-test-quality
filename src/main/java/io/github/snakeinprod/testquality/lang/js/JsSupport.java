package io.github.snakeinprod.testquality.lang.js;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Parsing and test extraction for JavaScript and TypeScript (JSX / TSX included): Jest, Vitest, Mocha, Jasmine and
 * Playwright test and suite functions.
 */
public class JsSupport implements LanguageSupport {

  private static final Set<String> TEST_FNS = Set.of("it", "test", "xit", "fit", "xtest", "specify", "xspecify");
  private static final Set<String> SUITE_FNS = Set.of("describe", "suite", "context", "xdescribe", "fdescribe", "xcontext");
  private static final Set<String> TEST_MODS = Set.of("only", "skip", "todo", "each", "concurrent", "fixme", "fail", "failing",
    "sequential");
  private static final Set<String> SUITE_MODS = Set.of("only", "skip", "todo", "each", "concurrent", "fixme", "parallel", "serial",
    "sequential", "shuffle");
  private static final Set<String> SKIP_MODS = Set.of("skip", "todo", "fixme");

  private enum Kind { TEST, SUITE }

  @Override
  public String family() {
    return "js";
  }

  @Override
  public List<String> languages() {
    return List.of("js", "ts");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    Lexed l = JsLexer.lex(path, language, source);
    return new ParsedFile(path, language, source, l.tokens(), l.comments());
  }

  @Override
  public TestFile extract(ParsedFile file) {
    List<TestCase> out = new ArrayList<>();
    visit(file.root(), "", false, wrappers(file.root()), out);
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  /**
   * {@code import ... from 'x'}, {@code import 'x'}, {@code export ... from 'x'}, {@code require('x')} and
   * {@code import('x')} of paths starting with "." or "/" (the base directory), and of workspace packages: a name that
   * a package.json in the project declares, to that package's entry, or {@code name/sub/path} to {@code sub/path} or
   * {@code src/sub/path} in its directory. Aliases such as tsconfig paths are not resolved. A path resolves as written,
   * with a JS or TS extension, as a directory index, or from ".js" to the ".ts" source.
   */
  @Override
  public Outline outline(ParsedFile file, Set<String> paths, Function<String, String> read) {
    Set<String> imports = new TreeSet<>();
    List<Token> t = file.tokens();
    for (int i = 1; i < t.size(); i++) {
      Token p = t.get(i - 1);
      if (t.get(i).kind() == TokenKind.STRING && (p.isIdent("from") || p.isIdent("import")
        || p.is("(") && i > 1 && (t.get(i - 2).isIdent("require") || t.get(i - 2).isIdent("import")))) {
        String spec = unquote(t.get(i).text());
        String r = spec.startsWith(".") || spec.startsWith("/") ? resolve(file.path(), spec, paths) : resolvePackage(spec, paths, read);
        if (r != null && !r.equals(file.path())) {
          imports.add(r);
        }
      }
    }
    List<Token> functions = new ArrayList<>();
    List<Token> classes = new ArrayList<>();
    declarations(file.root(), false, functions, classes);
    return new Outline(imports, functions, classes);
  }

  private static String resolve(String from, String spec, Set<String> paths) {
    if (!spec.startsWith(".") && !spec.startsWith("/")) {
      return null;
    }
    String base = Outline.join(spec.startsWith("/") ? "" : Outline.dir(from), spec);
    if (base == null) {
      return null;
    }
    List<String> candidates = new ArrayList<>(List.of(base));
    for (String ext : EXTENSIONS) {
      candidates.add(base + ext);
    }
    for (String ext : EXTENSIONS) {
      candidates.add(base + "/index" + ext);
    }
    if (base.matches(".*\\.[cm]?jsx?")) {
      String stem = base.substring(0, base.lastIndexOf('.'));
      candidates.addAll(List.of(stem + ".ts", stem + ".tsx", stem + ".mts", stem + ".cts"));
    }
    return candidates.stream().filter(paths::contains).findFirst().orElse(null);
  }

  /** {@code @scope/name[/sub]} or {@code name[/sub]} of a project package: its entry, or the sub path in it. */
  private String resolvePackage(String spec, Set<String> paths, Function<String, String> read) {
    int n = spec.startsWith("@") ? 2 : 1;
    String[] parts = spec.split("/", n + 1);
    String dir = parts.length < n ? null : packages(paths, read).get(n == 2 ? parts[0] + "/" + parts[1] : parts[0]);
    if (dir == null) {
      return null;
    }
    List<String> candidates = new ArrayList<>();
    if (parts.length > n) {
      candidates.add(parts[n]);
      candidates.add("src/" + parts[n]);
    } else {
      candidates.addAll(entries(read.apply(dir + "package.json")));
      candidates.addAll(List.of("src/index", "index"));
    }
    for (String c : candidates) {
      String r = resolve(dir, c.startsWith("./") ? c : "./" + c, paths);
      if (r != null && !r.matches(".*\\.d\\.[cm]?ts")) {
        return r;
      }
    }
    return null;
  }

  /** The entries a package.json names, in order: "exports" (a string or the "." entry), "module", "main", "source". */
  private static List<String> entries(String json) {
    List<String> out = new ArrayList<>();
    try {
      JsonObject o = JsonParser.parseString(json).getAsJsonObject();
      JsonElement exports = o.get("exports");
      if (exports != null && exports.isJsonObject() && exports.getAsJsonObject().has(".")) {
        exports = exports.getAsJsonObject().get(".");
      }
      condition(exports, out);
      for (String k : List.of("module", "main", "source")) {
        if (o.get(k) != null && o.get(k).isJsonPrimitive()) {
          out.add(o.get(k).getAsString());
        }
      }
    } catch (RuntimeException e) {
      // unreadable or not a JSON object: only the index fallback applies
    }
    return out;
  }

  /** A string export, or the import, default and types conditions of an object, nested ones too. */
  private static void condition(JsonElement e, List<String> out) {
    if (e != null && e.isJsonPrimitive()) {
      out.add(e.getAsString());
    } else if (e != null && e.isJsonObject()) {
      for (String k : List.of("import", "default", "types")) {
        condition(e.getAsJsonObject().get(k), out);
      }
    }
  }

  /** Package name to its directory ("" or "a/b/"): every directory above a project file with a named package.json. */
  private Map<String, String> packages(Set<String> paths, Function<String, String> read) {
    if (packages == null) {
      packages = new HashMap<>();
      Set<String> dirs = new TreeSet<>(Set.of(""));
      for (String p : paths) {
        for (int i = p.indexOf('/'); i >= 0; i = p.indexOf('/', i + 1)) {
          dirs.add(p.substring(0, i + 1));
        }
      }
      // sorted: a package above another of the same name wins
      for (String dir : dirs) {
        String json = dir.contains("node_modules/") ? null : read.apply(dir + "package.json");
        try {
          JsonElement name = json == null ? null : JsonParser.parseString(json).getAsJsonObject().get("name");
          if (name != null && name.isJsonPrimitive()) {
            packages.putIfAbsent(name.getAsString(), dir);
          }
        } catch (RuntimeException e) {
          // unreadable or not a JSON object: not a package
        }
      }
    }
    return packages;
  }

  private Map<String, String> packages;

  private static final List<String> EXTENSIONS = List.of(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".mts", ".cts");

  /**
   * Functions at the top level and in class bodies: {@code function f}, {@code x = () => ...} / {@code x = function},
   * methods {@code m(...) {...}}. Function literals passed as arguments do not count.
   */
  private static void declarations(Node.Group g, boolean classBody, List<Token> functions, List<Token> classes) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      Token t = Nodes.leaf(n);
      if (t == null) {
        continue;
      }
      if (t.isIdent("class")) {
        int j = i + 1;
        while (j < kids.size() && !(kids.get(j) instanceof Node.Group b && b.is("{"))) {
          j++;
        }
        if (j < kids.size()) {
          Token name = Nodes.leaf(kids.get(i + 1));
          classes.add(name != null && name.kind() == TokenKind.IDENT ? name : t);
          declarations((Node.Group) kids.get(j), true, functions, classes);
          i = j;
        }
      } else if (t.isIdent("function") || t.isIdent("async") && i + 1 < kids.size() && Nodes.isIdent(kids.get(i + 1), "function")) {
        JsTrees.Fn f = JsTrees.functionAt(g, i);
        if (f != null) {
          Token name = Nodes.leaf(kids.get(t.isIdent("async") ? i + 2 : i + 1));
          functions.add(name != null && name.kind() == TokenKind.IDENT ? name : t);
          i = f.end() - 1;
        }
      } else if (t.is("=") && i + 1 < kids.size() && !typeAlias(kids, i)) {
        JsTrees.Fn f = JsTrees.functionAt(g, i + 1);
        if (f != null) {
          Token name = i > 0 ? Nodes.leaf(kids.get(i - 1)) : null;
          functions.add(name != null ? name : t);
          i = f.end() - 1;
        }
      } else if (classBody && t.kind() == TokenKind.IDENT && i + 1 < kids.size() && kids.get(i + 1) instanceof Node.Group p
        && p.is("(")) {
        // a method: name(...) then a TypeScript return type, then the body
        int k = i + 2;
        while (k < kids.size() && kids.get(k) instanceof Node.Leaf l && !l.token().is(";") && !l.token().is("=")
          && !l.token().is(",") && !l.token().is("=>")) {
          k++;
        }
        if (k < kids.size() && kids.get(k) instanceof Node.Group b && b.is("{")) {
          functions.add(t);
          i = k;
        }
      }
    }
  }

  /** The "=" of {@code type F = (x: number) => void}. */
  private static boolean typeAlias(List<Node> kids, int eq) {
    for (int k = eq - 2; k >= 0 && k >= eq - 8 && kids.get(k) instanceof Node.Leaf l && !l.token().is(";"); k--) {
      if (l.token().isIdent("type") && (k == 0 || !Nodes.isLeaf(kids.get(k - 1), "."))) {
        return true;
      }
    }
    return false;
  }

  private void visit(Node.Group g, String suite, boolean skipped, Map<String, Wrapper> wrappers, List<TestCase> out) {
    Map<Integer, Chain> starts = new HashMap<>();
    for (Chain c : Chains.direct(g)) {
      starts.put(c.from(), c);
    }
    List<Node> kids = g.children();
    int i = 0;
    while (i < kids.size()) {
      Chain c = starts.get(i);
      if (c != null) {
        i = handle(c, suite, skipped, wrappers, out);
      } else {
        if (kids.get(i) instanceof Node.Group sub) {
          visit(sub, suite, skipped, wrappers, out);
        }
        i++;
      }
    }
  }

  /** Records a test or walks into a suite or other chain; returns the child index to continue from. */
  private int handle(Chain c, String suite, boolean skipped, Map<String, Wrapper> wrappers, List<TestCase> out) {
    List<Chain.Segment> segs = c.segments();
    List<String> names = leadingNames(c);
    Kind kind = kind(names);
    int k = names.size();
    Wrapper wrapper = kind == null && k == 1 && k < segs.size() && segs.get(k) instanceof Chain.Call ? wrappers.get(names.get(0)) : null;
    if (wrapper != null) {
      return wrapped(c, wrapper, ((Chain.Call) segs.get(k)).args(), suite, skipped, wrappers, out);
    }
    if (wrappers.values().stream().anyMatch(w -> c.first().equals(w.inner()))) {
      // the wrapper's own call: the wrapper's callers are the tests
      return walkChain(c, suite, skipped, wrappers, out);
    }
    int end = c.to();
    Node.Group args = null;
    boolean each = names.contains("each");
    if (kind != null && each) {
      List<Node> kids = c.container().children();
      if (k + 1 < segs.size() && segs.get(k) instanceof Chain.Call && segs.get(k + 1) instanceof Chain.Call call) {
        args = call.args();
      } else if (k == segs.size() && end + 1 < kids.size() && Nodes.leaf(kids.get(end)) != null
        && Nodes.leaf(kids.get(end)).kind() == TokenKind.STRING && kids.get(end + 1) instanceof Node.Group g && g.is("(")) {
        // it.each`table`(name, fn)
        args = g;
        end += 2;
      }
    } else if (kind != null && k < segs.size() && segs.get(k) instanceof Chain.Call call) {
      args = call.args();
    }
    List<List<Node>> items = args == null ? List.of() : args.items();
    if (items.isEmpty() || JsTrees.functionAt(args, 0) != null) {
      return walkChain(c, suite, skipped, wrappers, out);
    }
    JsTrees.Fn fn = callback(args);
    boolean todo = names.contains("todo");
    boolean reference = items.size() > 1 && items.get(items.size() - 1).size() == 1
      && Nodes.leaf(items.get(items.size() - 1).get(0)) != null
      && Nodes.leaf(items.get(items.size() - 1).get(0)).kind() == TokenKind.IDENT;
    if (fn == null && !todo && !reference) {
      return walkChain(c, suite, skipped, wrappers, out);
    }
    List<Node> nameItem = items.get(0);
    Token nameToken = nameItem.get(0).first();
    String name = nameItem.size() == 1 && nameToken.kind() == TokenKind.STRING ? unquote(nameToken.text()) : Nodes.text(nameItem);
    boolean skip = skipped || names.get(0).startsWith("x") || names.stream().anyMatch(SKIP_MODS::contains);
    if (kind == Kind.SUITE) {
      if (fn != null) {
        visit(fn.body(), suite.isEmpty() ? name : suite + " > " + name, skip, wrappers, out);
      }
      return end;
    }
    // a single plain parameter of a non-table test is the done callback
    boolean done = fn != null && !each && fn.paramCount() == 1 && fn.simpleParams().size() == 1;
    out.add(test(name, suite, nameToken, c, args, fn, skip, done));
    return end;
  }

  private static TestCase test(String name, String suite, Token nameToken, Chain c, Node.Group args, JsTrees.Fn fn, boolean skip,
    boolean done) {
    int assertions = 0;
    int mocks = 0;
    if (fn != null) {
      assertions = JsTrees.assertCalls(fn.body());
      for (Chain ch : Chains.in(fn.body())) {
        assertions += JsTrees.isAssertion(ch) ? 1 : 0;
        mocks += JsTrees.isMock(ch) ? 1 : 0;
      }
    }
    Token last = args.close() != null ? args.close() : args.last();
    return new TestCase(name, suite, nameToken, c.first(), last, fn == null ? null : fn.body(), skip, fn != null && fn.async() || done,
      assertions, mocks);
  }

  /**
   * A function of the same file that passes its own parameters to a test function as name and body, such as
   * {@code function itWithUser(name, fn) { it(name, async () => fn(await login())); }} or
   * {@code const itAsAdmin = (name, fn) => test(name, fn)}. Each call of it with a literal name and a function is a test;
   * its own call of the test function is not.
   *
   * @param name  index of the parameter holding the test name
   * @param body  index of the parameter holding the test body
   * @param inner the wrapper's call of the test function
   */
  private record Wrapper(int name, int body, Token inner) {
  }

  /** The wrappers declared in the file, by name. */
  private static Map<String, Wrapper> wrappers(Node.Group root) {
    Map<String, Wrapper> out = new HashMap<>();
    Nodes.groups(root, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token t = Nodes.leaf(kids.get(i));
        Token name = null;
        JsTrees.Fn fn = null;
        if (t != null && t.isIdent("function")) {
          name = Nodes.leaf(kids.get(i + 1));
          fn = JsTrees.functionAt(g, i);
        } else if (t != null && t.is("=") && i > 0) {
          name = Nodes.leaf(kids.get(i - 1));
          fn = JsTrees.functionAt(g, i + 1);
        }
        Wrapper w = fn == null || fn.params() == null || name == null || name.kind() != TokenKind.IDENT
          || kind(List.of(name.text())) != null ? null : wrapper(fn);
        if (w != null) {
          out.put(name.text(), w);
        }
      }
    });
    return out;
  }

  /** The test call of a function whose name argument is a parameter and whose body is another parameter or calls it. */
  private static Wrapper wrapper(JsTrees.Fn fn) {
    List<String> params = fn.params().items().stream().map(item -> Nodes.text(item.get(0))).toList();
    for (Chain c : Chains.in(fn.body())) {
      List<String> names = leadingNames(c);
      if (kind(names) != Kind.TEST || names.contains("each") || names.size() >= c.segments().size()
        || !(c.segments().get(names.size()) instanceof Chain.Call call)) {
        continue;
      }
      List<List<Node>> items = call.items();
      int name = items.size() < 2 || items.get(0).size() != 1 ? -1 : params.indexOf(Nodes.text(items.get(0)));
      if (name < 0) {
        continue;
      }
      for (List<Node> item : items.subList(1, items.size())) {
        int body = item.size() == 1 ? params.indexOf(Nodes.text(item)) : -1;
        for (Chain b : body >= 0 ? List.<Chain>of() : Chains.in(new Node.Group(null, null, item))) {
          int p = params.indexOf(b.firstName());
          body = body < 0 && p >= 0 && p != name && b.callAfter(0) != null ? p : body;
        }
        if (body >= 0 && body != name) {
          return new Wrapper(name, body, c.first());
        }
      }
    }
    return null;
  }

  /** A call of a wrapper: a test when the name is a string literal and the body a function literal. */
  private int wrapped(Chain c, Wrapper w, Node.Group args, String suite, boolean skipped, Map<String, Wrapper> wrappers,
    List<TestCase> out) {
    List<List<Node>> items = args.items();
    JsTrees.Fn fn = null;
    int item = 0;
    List<Node> kids = args.children();
    for (int i = 0; i < kids.size() && fn == null; i++) {
      if (Nodes.isLeaf(kids.get(i), ",") && Nodes.leaf(kids.get(i)).kind() == TokenKind.OP) {
        item++;
      } else if (item == w.body() && (i == 0 || Nodes.isLeaf(kids.get(i - 1), ","))) {
        fn = JsTrees.functionAt(args, i);
      }
    }
    Token nameToken = items.size() > w.name() && items.get(w.name()).size() == 1 ? items.get(w.name()).get(0).first() : null;
    if (fn == null || nameToken == null || nameToken.kind() != TokenKind.STRING) {
      return walkChain(c, suite, skipped, wrappers, out);
    }
    out.add(test(unquote(nameToken.text()), suite, nameToken, c, args, fn, skipped, false));
    return c.to();
  }

  /** The names before the first call or index: [it, only] for {@code it.only("x", fn)}. */
  private static List<String> leadingNames(Chain c) {
    List<String> names = new ArrayList<>();
    while (names.size() < c.segments().size() && c.segments().get(names.size()) instanceof Chain.Name n) {
      names.add(n.token().text());
    }
    return names;
  }

  /** Not a test or suite: walks into the chain's call arguments and index groups. */
  private int walkChain(Chain c, String suite, boolean skipped, Map<String, Wrapper> wrappers, List<TestCase> out) {
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Call call) {
        visit(call.args(), suite, skipped, wrappers, out);
      } else if (s instanceof Chain.Index idx) {
        visit(idx.index(), suite, skipped, wrappers, out);
      }
    }
    return c.to();
  }

  /** The first function literal after the name argument. */
  private static JsTrees.Fn callback(Node.Group args) {
    List<Node> kids = args.children();
    int i = 0;
    while (i < kids.size() && !(Nodes.isLeaf(kids.get(i), ",") && Nodes.leaf(kids.get(i)).kind() == TokenKind.OP)) {
      i++;
    }
    for (i++; i < kids.size(); i++) {
      JsTrees.Fn fn = JsTrees.functionAt(args, i);
      if (fn != null) {
        return fn;
      }
    }
    return null;
  }

  private static Kind kind(List<String> names) {
    if (names.isEmpty()) {
      return null;
    }
    String first = names.get(0);
    List<String> rest = names.subList(1, names.size());
    if (first.equals("test") && !rest.isEmpty() && rest.get(0).equals("describe")) {
      return SUITE_MODS.containsAll(rest.subList(1, rest.size())) ? Kind.SUITE : null;
    }
    if (TEST_FNS.contains(first)) {
      return TEST_MODS.containsAll(rest) ? Kind.TEST : null;
    }
    if (SUITE_FNS.contains(first)) {
      return SUITE_MODS.containsAll(rest) ? Kind.SUITE : null;
    }
    return null;
  }

  private static String unquote(String s) {
    return s.length() >= 2 && s.charAt(s.length() - 1) == s.charAt(0) ? s.substring(1, s.length() - 1) : s;
  }
}
