package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** An assertion inside .then, setTimeout or an event handler that the test does not wait for may never run. */
public class AssertionInUnawaitedCallback implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
