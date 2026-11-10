package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** when(...).thenReturn(...) stubs that the test never triggers. */
public class StubNeverUsed implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
