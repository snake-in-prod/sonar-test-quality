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
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Statements after the call that raises inside a pytest.raises block never run. Reports the assertions that end a
 * {@code with pytest.raises(...)} / {@code with self.assertRaises(...)} block right after a statement with a call.
 */
public class AssertionInsideRaises implements TestRule {

  private static final Set<String> KW = Chains.keywords("py");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null) {
      return;
    }
    for (TestCase t : ctx.tests().tests()) {
      if (t.body() instanceof PyStatement def) {
        visit(ctx, m, def.body());
      }
    }
  }

  private static void visit(RuleContext ctx, PyModule m, List<PyStatement> block) {
    for (PyStatement s : block) {
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      if (kw.equals("with")) {
        Chain raises = raises(m, s);
        if (raises != null) {
          checkBlock(ctx, raises, s.body());
        }
      }
      visit(ctx, m, s.body());
    }
  }

  private static void checkBlock(RuleContext ctx, Chain raises, List<PyStatement> body) {
    int k = body.size();
    while (k > 0 && isAssertion(body.get(k - 1)) && !isFailIdiom(body.get(k - 1))) {
      k--;
    }
    if (k == 0 || k == body.size()) {
      return;
    }
    PyStatement raising = body.get(k - 1);
    if (!raising.body().isEmpty() || isAssertion(raising)) {
      return;
    }
    Chain call = Chains.in(raising.group(), KW).stream().filter(c -> !c.calls().isEmpty()).findFirst().orElse(null);
    if (call == null) {
      return;
    }
    String with = String.join(".", head(raises));
    for (PyStatement a : body.subList(k, body.size())) {
      ctx.report(a.first(), a.last(), "This assertion never runs because \"" + code(ctx.file(), call)
        + "\" raises first; move it after the \"with " + with + "(...)\" block.");
    }
  }

  /** The {@code pytest.raises(...)} or {@code self.assertRaises*(...)} call of a with header, unless it expects AssertionError. */
  private static Chain raises(PyModule m, PyStatement with) {
    if (Nodes.tokens(with.nodes()).stream().anyMatch(t -> t.isIdent("if"))) {
      // "pytest.raises(E) if bad else nullcontext()": the assertions run in the other case
      return null;
    }
    for (Chain c : Chains.in(with.group(), KW)) {
      List<String> head = head(c);
      boolean pytest = m.resolve(String.join(".", head)).equals("pytest.raises");
      boolean unittest = head.size() == 2 && head.get(0).equals("self") && head.get(1).startsWith("assertRaises");
      if ((pytest || unittest) && c.callAfter(head.size() - 1) != null) {
        boolean assertionError = Nodes.tokens(c.callAfter(head.size() - 1).args()).stream().anyMatch(t -> t.is("AssertionError"));
        return assertionError ? null : c;
      }
    }
    return null;
  }

  /** {@code assert ...}, {@code self.assert*(...)}, {@code self.fail(...)}, {@code m.assert_*(...)} as a statement. */
  static boolean isAssertion(PyStatement s) {
    if (s.keyword().equals("assert")) {
      return true;
    }
    if (!s.body().isEmpty()) {
      return false;
    }
    List<Chain> chains = Chains.direct(s.group(), KW);
    if (chains.isEmpty() || chains.get(0).from() != 0 || chains.get(0).to() != s.nodes().size()) {
      return false;
    }
    Chain c = chains.get(0);
    List<String> head = head(c);
    if (c.calls().isEmpty() || head.size() < 2) {
      return false;
    }
    String last = head.get(head.size() - 1);
    return head.size() == 2 && head.get(0).equals("self") && (last.startsWith("assert") || last.equals("fail")) || last.startsWith("assert_");
  }

  /** {@code assert False}, {@code assert 0} and {@code self.fail(...)}: the "should have raised" guard, harmless here. */
  private static boolean isFailIdiom(PyStatement s) {
    List<Node> n = s.nodes();
    if (s.keyword().equals("assert")) {
      return n.size() >= 2 && (Nodes.isIdent(n.get(1), "False") || Nodes.isLeaf(n.get(1), "0"))
        && (n.size() == 2 || Nodes.isLeaf(n.get(2), ","));
    }
    return n.size() >= 3 && Nodes.isIdent(n.get(0), "self") && Nodes.isIdent(n.get(2), "fail");
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
