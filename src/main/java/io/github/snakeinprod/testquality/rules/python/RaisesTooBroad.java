package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * pytest.raises(Exception), or a block with several calls, lets the wrong line raise and the test still pass. Stays
 * silent when the exception is narrowed by {@code match=} or by an {@code as} name the test reads afterwards.
 */
public class RaisesTooBroad implements TestRule {

  private static final Set<String> KW = Chains.keywords("py");
  private static final Set<String> BROAD = Set.of("Exception", "BaseException");
  /** Calls that build values and practically never raise the expected exception. */
  private static final Set<String> HARMLESS = Set.of("str", "int", "float", "bool", "bytes", "len", "list", "dict", "tuple", "set",
    "frozenset", "range", "repr", "sorted", "print", "isinstance", "object", "datetime", "date", "timedelta");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null) {
      return;
    }
    for (TestCase t : ctx.tests().tests()) {
      if (t.body() instanceof PyStatement def) {
        visit(ctx, m, t, def.body());
      }
    }
  }

  private static void visit(RuleContext ctx, PyModule m, TestCase t, List<PyStatement> block) {
    for (PyStatement s : block) {
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      for (Chain c : Chains.in(s.group(), KW)) {
        Chain.Call call = raisesCall(m, c);
        if (call != null) {
          check(ctx, t, s, c, call);
        }
      }
      visit(ctx, m, t, s.body());
    }
  }

  private static void check(RuleContext ctx, TestCase t, PyStatement s, Chain c, Chain.Call call) {
    List<List<Node>> args = call.items();
    if (args.isEmpty() || args.stream().anyMatch(a -> Nodes.isIdent(a.get(0), "match") && a.size() > 1 && Nodes.isLeaf(a.get(1), "="))) {
      return;
    }
    boolean with = s.keyword().equals("with");
    if (with && aliasRead(ctx.file(), t, s, c)) {
      return;
    }
    String code = code(ctx.file(), c);
    List<Node> exc = args.get(0);
    if (with && raisesItself(s, exc)) {
      // the test raises it on purpose, to check that a context manager lets it through
      return;
    }
    if (exc.size() == 1 && Nodes.leaf(exc.get(0)) != null && Nodes.leaf(exc.get(0)).kind() == TokenKind.IDENT
      && BROAD.contains(Nodes.leaf(exc.get(0)).text())) {
      Token e = Nodes.leaf(exc.get(0));
      ctx.report(e, "\"" + code + "\" passes on any error, also one from the wrong line; name the exception the code should raise.");
      return;
    }
    if (!with || s.body().stream().anyMatch(b -> !b.body().isEmpty())) {
      return;
    }
    long calls = s.body().stream().filter(b -> !AssertionInsideRaises.isAssertion(b) && callsSomething(b)).count();
    if (calls >= 2) {
      ctx.report(c.first(), c.last(), "\"" + code + "\" wraps " + calls + " statements that call code, so the wrong one can raise; "
        + "move the setup out and keep only the call that should raise.");
    }
  }

  /** The call segment of {@code pytest.raises(...)} or {@code self.assertRaises(...)}, or null. */
  private static Chain.Call raisesCall(PyModule m, Chain c) {
    List<String> head = head(c);
    boolean pytest = m.resolve(String.join(".", head)).equals("pytest.raises");
    boolean unittest = head.size() == 2 && head.get(0).equals("self") && head.get(1).equals("assertRaises");
    return pytest || unittest ? c.callAfter(head.size() - 1) : null;
  }

  /**
   * {@code with raises(...) as name:} (also as one item of a parenthesized {@code with (...)}) and the test reads that
   * name after the header (to check the type or message).
   */
  private static boolean aliasRead(ParsedFile f, TestCase t, PyStatement with, Chain raises) {
    List<Token> tokens = f.tokens();
    int i = tokens.indexOf(raises.last());
    if (i < 0 || i + 2 >= tokens.size() || !tokens.get(i + 1).isIdent("as") || tokens.get(i + 2).kind() != TokenKind.IDENT) {
      return false;
    }
    String alias = tokens.get(i + 2).text();
    Token headerEnd = with.nodes().get(with.nodes().size() - 1).last();
    return tokens.stream().anyMatch(tok -> tok.isIdent(alias) && after(tok, headerEnd) && !after(tok, t.end()));
  }

  private static boolean after(Token a, Token b) {
    return a.line() > b.line() || a.line() == b.line() && a.column() > b.column();
  }

  /** The block holds {@code raise E(...)} or {@code raise E} of the expected class {@code E} as written. */
  private static boolean raisesItself(PyStatement with, List<Node> exception) {
    String expected = Nodes.text(exception);
    return with.walk().stream().anyMatch(x -> {
      List<Node> n = x.nodes();
      if (!x.keyword().equals("raise") || n.size() < 2) {
        return false;
      }
      int end = n.size();
      if (n.get(end - 1) instanceof Node.Group g && g.is("(")) {
        end--;
      }
      return Nodes.text(n.subList(1, end)).equals(expected);
    });
  }

  private static boolean callsSomething(PyStatement s) {
    return Chains.in(s.group(), KW).stream().anyMatch(c -> !c.calls().isEmpty() && !(c.segments().size() == 2 && HARMLESS.contains(c.firstName())));
  }

  private static List<String> head(Chain c) {
    List<String> out = new ArrayList<>();
    for (Chain.Segment s : c.segments()) {
      if (!(s instanceof Chain.Name n)) {
        break;
      }
      out.add(n.token().text());
    }
    return out;
  }

  /** The chain as written when it fits on one line, else its names and "(...)". */
  private static String code(ParsedFile f, Chain c) {
    Token from = c.first();
    Token to = c.last();
    return from.line() == to.endLine() ? f.lines().get(from.line() - 1).substring(from.column(), to.endColumn()) : c.path() + "(...)";
  }
}
