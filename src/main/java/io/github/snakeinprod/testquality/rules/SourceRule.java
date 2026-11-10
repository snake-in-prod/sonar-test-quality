package io.github.snakeinprod.testquality.rules;

/**
 * A rule on source files instead of test files. It runs in the analyzer's second pass, after every test file, with
 * {@link RuleContext#outline()} set and no tests.
 */
public interface SourceRule extends TestRule {
}
