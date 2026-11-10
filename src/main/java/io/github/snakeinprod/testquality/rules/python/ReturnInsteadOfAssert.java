package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A test that returns its comparison passes whatever the result is. */
public class ReturnInsteadOfAssert implements TestRule {

  private static final Set<String> CONDITIONAL = Set.of("if", "lambda");

  @Override
  public void check(RuleContext ctx) {
    Set<String> names = new HashSet<>();
    ctx.tests().tests().forEach(t -> names.add(t.name()));
    Map<String, List<Token>> uses = new HashMap<>();
    for (Token x : ctx.file().tokens()) {
      if (x.kind() == TokenKind.IDENT && names.contains(x.text())) {
        uses.computeIfAbsent(x.text(), k -> new ArrayList<>()).add(x);
      }
    }
    for (TestCase t : ctx.tests().tests()) {
      // the test function is also used as a helper by other code of the file, which may check what it returns
      if (uses.getOrDefault(t.name(), List.of()).stream().allMatch(x -> x == t.nameToken())) {
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
}
