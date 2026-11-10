package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A getBy or queryBy right after an action that renders later fails or checks the old screen. */
public class SyncQueryForAsyncUi implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
