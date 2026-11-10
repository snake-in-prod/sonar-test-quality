package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Statements after the call that raises inside a pytest.raises block never run. */
public class AssertionInsideRaises implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
