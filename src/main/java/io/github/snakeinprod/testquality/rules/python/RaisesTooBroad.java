package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** pytest.raises(Exception), or a block with several calls, lets the wrong line raise and the test still pass. */
public class RaisesTooBroad implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
