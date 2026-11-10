package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/** An attribute or method of a mock used in an assert is always truthy, so the assertion never fails. */
public class MockAttributeAsAssertion implements TestRule {

  private static final Set<String> KEYWORDS = Chains.keywords("py");

  @Override
  public void check(RuleContext ctx) {
    Set<String> local = MockAssertionTypo.definedNames(ctx.file());
    for (TestCase t : ctx.tests().tests()) {
      for (PyStatement s : MockAssertionTypo.statements(t)) {
        List<Node> nodes = s.nodes();
        if (s.keyword().equals("assert") && nodes.size() > 1) {
          List<List<Node>> parts = Nodes.split(nodes.subList(1, nodes.size()), ",");
          if (!parts.isEmpty()) {
            operands(ctx, parts.get(0), local);
          }
        }
        for (Chain c : Chains.in(s.group(), KEYWORDS)) {
          Chain.Call call = c.names().size() == 2 && c.firstName().equals("self") && c.lastName().equals("assertTrue")
            ? c.callAfter(1) : null;
          if (call != null && !call.items().isEmpty()) {
            operand(ctx, call.items().get(0), local);
          }
        }
      }
    }
  }

  /** The operands of an asserted expression, split on "and" / "or". */
  private static void operands(RuleContext ctx, List<Node> expr, Set<String> local) {
    int from = 0;
    for (int i = 0; i <= expr.size(); i++) {
      if (i == expr.size() || Nodes.isIdent(expr.get(i), "and") || Nodes.isIdent(expr.get(i), "or")) {
        if (i > from) {
          operand(ctx, expr.subList(from, i), local);
        }
        from = i + 1;
      }
    }
  }

  private static void operand(RuleContext ctx, List<Node> part, Set<String> local) {
    if (part.size() == 1 && part.get(0) instanceof Node.Group g && g.is("(")) {
      operands(ctx, g.children(), local);
      return;
    }
    List<Chain> chains = Chains.direct(new Node.Group(null, null, part), KEYWORDS);
    if (chains.size() != 1 || chains.get(0).from() != 0 || chains.get(0).to() != part.size()) {
      return;
    }
    Chain c = chains.get(0);
    String name = c.lastName();
    if (c.names().size() < 2 || !(c.segments().get(c.segments().size() - 1) instanceof Chain.Name n) || local.contains(name)) {
      return;
    }
    String code = code(part);
    String receiver = code.substring(0, code.length() - name.length());
    String message;
    if (MockAssertionTypo.ASSERTIONS.contains(name)) {
      message = "\"" + code + "\" is a method that is never called, so this assertion always passes; call " + code + "() instead.";
    } else if (MockAssertionTypo.PSEUDO.contains(name) && !name.equals("called_once")) {
      // requests_mock has a real "called_once" property
      message = "\"" + code + "\" is an attribute a mock creates on access, so this assertion always passes; call "
        + receiver + "assert_" + name + "(...) instead.";
    } else if (name.startsWith("assert_")) {
      String near = MockAssertionTypo.nearest(name);
      message = "\"" + code + "\" is a method that is never called, so this assertion always passes; call "
        + receiver + (near != null ? near : name) + "() instead.";
    } else {
      return;
    }
    ctx.report(n.token(), message);
  }

  /** The source text of the nodes without spaces: "m.save.assert_called". */
  private static String code(List<Node> nodes) {
    StringBuilder sb = new StringBuilder();
    Nodes.tokens(nodes).forEach(t -> sb.append(t.text()));
    return sb.toString();
  }
}
