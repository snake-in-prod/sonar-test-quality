package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** An expect that is never finished with a matcher checks nothing. */
public class ExpectWithoutMatcher implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
