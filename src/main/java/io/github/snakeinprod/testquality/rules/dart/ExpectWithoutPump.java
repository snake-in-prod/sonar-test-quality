package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** An expect on the widget tree right after an action with no pump in between checks the old frame. */
public class ExpectWithoutPump implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
