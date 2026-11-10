package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A jest.fn() or vi.fn() created and passed in but never asserted on. */
public class MockNeverAsserted implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
