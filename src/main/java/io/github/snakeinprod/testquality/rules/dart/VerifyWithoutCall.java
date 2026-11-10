package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A verify or when whose argument is not a call on a mock checks nothing. */
public class VerifyWithoutCall implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
