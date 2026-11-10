package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Two tests with the same description in one group: filters and reports hit both or the wrong one. */
public class DuplicateTestName implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
