package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** expectLater returns a future; not awaited, the test ends before it checks. */
public class UnawaitedExpectLater implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
