package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A matcher that cannot fail on the value, such as anything. */
public class TrivialMatcher implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
