package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A .resolves / .rejects or Playwright expect that is neither awaited nor returned is not waited for, so its failure is lost. */
public class UnawaitedAsyncAssertion implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
