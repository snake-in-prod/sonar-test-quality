package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A test that takes done and is async or returns a promise: Jest fails or waits on the wrong one. */
public class DoneAndPromise implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
