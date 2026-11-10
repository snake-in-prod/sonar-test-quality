package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A patched or created mock that the test neither passes on nor asserts on. */
public class MockNeverUsed implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
