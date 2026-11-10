package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;

/** completion, throwsA or emits on a future or stream in a test that ends before it settles may never check. */
public class AsyncMatcherNotAwaited implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    // not implemented yet
  }
}
