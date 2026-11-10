package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** Patching the module that defines a name has no effect on code that imported the name with from-import. */
public class PatchWhereDefined implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
