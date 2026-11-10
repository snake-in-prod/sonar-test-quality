package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A misspelled assert method on a mock, or a non-assert method used as one (called_once_with), is accepted by a plain Mock and silently checks nothing. */
public class MockAssertionTypo implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
