package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.model.Outline;

/** A source file with at least 3 functions or methods that no test imports, directly or through one source file. */
public class SourceWithoutTest implements SourceRule {

  @Override
  public void check(RuleContext ctx) {
    Outline o = ctx.outline();
    if (o != null && !ctx.reached() && o.functions().size() >= 3) {
      ctx.report(o.functions().get(0), "No test imports this file or a file that imports it; add tests for its "
        + o.functions().size() + " functions.");
    }
  }
}
