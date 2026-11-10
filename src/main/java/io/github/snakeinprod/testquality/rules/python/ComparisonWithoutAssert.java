package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A comparison statement whose result is dropped checks nothing. */
public class ComparisonWithoutAssert implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
