package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A mock created and injected with no verify and no stub that affects an assertion. */
public class MockNeverVerified implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
