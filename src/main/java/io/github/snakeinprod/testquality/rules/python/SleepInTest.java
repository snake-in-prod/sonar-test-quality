package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** time.sleep in a test makes it slow, and flaky when it waits for something. */
public class SleepInTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
