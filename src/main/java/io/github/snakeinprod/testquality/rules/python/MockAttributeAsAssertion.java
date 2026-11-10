package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** An attribute or method of a mock used in an assert is always truthy, so the assertion never fails. */
public class MockAttributeAsAssertion implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
