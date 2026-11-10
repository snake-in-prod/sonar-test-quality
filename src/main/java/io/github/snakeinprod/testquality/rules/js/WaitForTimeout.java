package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** page.waitForTimeout or a fixed setTimeout delay makes tests slow and flaky. */
public class WaitForTimeout implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
