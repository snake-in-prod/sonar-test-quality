package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A test whose only assertion is a snapshot accepts whatever was recorded first. */
public class SnapshotOnlyTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
