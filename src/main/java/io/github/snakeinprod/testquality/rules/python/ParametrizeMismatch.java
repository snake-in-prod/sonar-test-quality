package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Names that do not match the test parameters, or rows of the wrong length, make pytest error or test the wrong thing. */
public class ParametrizeMismatch implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
