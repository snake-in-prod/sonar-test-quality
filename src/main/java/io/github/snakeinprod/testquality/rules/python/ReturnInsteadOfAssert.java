package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/** A test that returns its comparison passes whatever the result is. */
public class ReturnInsteadOfAssert implements TestRule {

  private static final Set<String> CONDITIONAL = Set.of("if", "lambda");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      if (!calledElsewhere(ctx, t)) {
        returns(ctx, ((PyStatement) t.body()).body());
      }
    }
  }

  /** Return statements of the test itself; nested functions and classes return to their own callers. */
  private static void returns(RuleContext ctx, List<PyStatement> statements) {
    for (PyStatement s : statements) {
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      List<Node> nodes = s.nodes();
      if (kw.equals("return") && nodes.size() > 1) {
        List<Node> value = nodes.subList(1, nodes.size());
        if (ComparisonWithoutAssert.isComparison(value) && !ComparisonWithoutAssert.hasTop(value, CONDITIONAL)) {
          Token from = nodes.get(0).first();
          Token to = nodes.get(nodes.size() - 1).last();
          ctx.report(from, to, "The test returns \"" + ComparisonWithoutAssert.code(ctx.file(), value.get(0).first(), to)
            + "\" instead of asserting it, so it passes whatever the result; replace \"return\" with \"assert\".");
        }
      }
      returns(ctx, s.body());
    }
  }

  /** The test function is also used as a helper by other code of the file, which may check what it returns. */
  private static boolean calledElsewhere(RuleContext ctx, TestCase t) {
    return ctx.file().tokens().stream().anyMatch(x -> x != t.nameToken() && x.isIdent(t.name()));
  }
}
