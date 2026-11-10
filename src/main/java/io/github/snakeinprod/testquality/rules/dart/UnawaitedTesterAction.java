package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A tap, enterText, pump or pumpWidget that is not awaited lets the test check the screen before it changed. */
public class UnawaitedTesterAction implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
