package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A count that does not match the expect calls makes the test fail for the wrong reason or hides a skipped assertion. */
public class ExpectAssertionsMismatch implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
