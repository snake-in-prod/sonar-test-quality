package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** A function or class with assertions that pytest or unittest never collects because of its name. */
public class UndiscoveredTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
