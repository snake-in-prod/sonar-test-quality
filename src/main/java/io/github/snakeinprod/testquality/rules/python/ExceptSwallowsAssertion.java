package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** try / except around an assertion catches its failure, so the test passes. */
public class ExceptSwallowsAssertion implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
