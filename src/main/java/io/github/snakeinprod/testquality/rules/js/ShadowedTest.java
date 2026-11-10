package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Two tests with the same name in one describe: reports and filters hit only one. */
public class ShadowedTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
