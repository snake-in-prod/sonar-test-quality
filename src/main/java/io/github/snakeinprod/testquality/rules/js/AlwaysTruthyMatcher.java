package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A matcher that cannot fail on the value given checks nothing. */
public class AlwaysTruthyMatcher implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
