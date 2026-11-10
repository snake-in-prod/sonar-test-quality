package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** random or uuid4 values feeding an assertion without a fixed seed make the test flaky. */
public class UnseededRandom implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
