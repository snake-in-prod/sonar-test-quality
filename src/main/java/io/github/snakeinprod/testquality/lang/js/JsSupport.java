package io.github.snakeinprod.testquality.lang.js;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    visit(file.root(), "", false, out);
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  private void visit(Node.Group g, String suite, boolean skipped, List<TestCase> out) {
    Map<Integer, Chain> starts = new HashMap<>();
    for (Chain c : Chains.direct(g)) {
      starts.put(c.from(), c);
    }
    List<Node> kids = g.children();
    int i = 0;
    while (i < kids.size()) {
      Chain c = starts.get(i);
      if (c != null) {
        i = handle(c, suite, skipped, out);
      } else {
        if (kids.get(i) instanceof Node.Group sub) {
          visit(sub, suite, skipped, out);
        }
        i++;
      }
    }
  }

  /** Records a test or walks into a suite or other chain; returns the child index to continue from. */
  private int handle(Chain c, String suite, boolean skipped, List<TestCase> out) {
    List<Chain.Segment> segs = c.segments();
    List<String> names = new ArrayList<>();
    while (names.size() < segs.size() && segs.get(names.size()) instanceof Chain.Name n) {
      names.add(n.token().text());
    }
    Kind kind = kind(names);
    int k = names.size();
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
      return walkChain(c, suite, skipped, out);
    }
    JsTrees.Fn fn = callback(args);
    boolean todo = names.contains("todo");
    boolean reference = items.size() > 1 && items.get(items.size() - 1).size() == 1
      && Nodes.leaf(items.get(items.size() - 1).get(0)) != null
      && Nodes.leaf(items.get(items.size() - 1).get(0)).kind() == TokenKind.IDENT;
    if (fn == null && !todo && !reference) {
      return walkChain(c, suite, skipped, out);
    }
    List<Node> nameItem = items.get(0);
    Token nameToken = nameItem.get(0).first();
    String name = nameItem.size() == 1 && nameToken.kind() == TokenKind.STRING ? unquote(nameToken.text()) : Nodes.text(nameItem);
    boolean skip = skipped || names.get(0).startsWith("x") || names.stream().anyMatch(SKIP_MODS::contains);
    if (kind == Kind.SUITE) {
      if (fn != null) {
        visit(fn.body(), suite.isEmpty() ? name : suite + " > " + name, skip, out);
      }
      return end;
    }
    int assertions = 0;
    int mocks = 0;
    boolean async = false;
    if (fn != null) {
      assertions = JsTrees.assertCalls(fn.body());
      for (Chain ch : Chains.in(fn.body())) {
        assertions += JsTrees.isAssertion(ch) ? 1 : 0;
        mocks += JsTrees.isMock(ch) ? 1 : 0;
      }
      // a single plain parameter of a non-table test is the done callback
      boolean done = !each && fn.paramCount() == 1 && fn.simpleParams().size() == 1;
      async = fn.async() || done;
    }
    Token last = args.close() != null ? args.close() : args.last();
    out.add(new TestCase(name, suite, nameToken, c.first(), last, fn == null ? null : fn.body(), skip, async, assertions, mocks));
    return end;
  }

  /** Not a test or suite: walks into the chain's call arguments and index groups. */
  private int walkChain(Chain c, String suite, boolean skipped, List<TestCase> out) {
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Call call) {
        visit(call.args(), suite, skipped, out);
      } else if (s instanceof Chain.Index idx) {
        visit(idx.index(), suite, skipped, out);
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
