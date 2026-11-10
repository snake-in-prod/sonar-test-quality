package io.github.snakeinprod.testquality.rules;

/** One rule. Instances are created per analysis and may keep state between files only through the context. */
public interface TestRule {

  void check(RuleContext ctx);
}
