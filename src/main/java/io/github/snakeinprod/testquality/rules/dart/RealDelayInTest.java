package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Future.delayed, sleep or a real Timer in a widget test instead of tester.pump(duration) or fake async. */
public class RealDelayInTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
