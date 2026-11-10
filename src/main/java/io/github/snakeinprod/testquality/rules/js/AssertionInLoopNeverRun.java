package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Assertions only inside a loop over a collection that can be empty pass when it is empty. */
public class AssertionInLoopNeverRun implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
