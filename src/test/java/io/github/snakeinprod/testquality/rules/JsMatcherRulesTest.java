package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class JsMatcherRulesTest {

  @Test
  void expect_without_matcher() {
    RuleVerifier.verify("js", "ExpectWithoutMatcher");
  }

  @Test
  void always_truthy_matcher() {
    RuleVerifier.verify("js", "AlwaysTruthyMatcher");
  }

  @Test
  void snapshot_only_test() {
    RuleVerifier.verify("js", "SnapshotOnlyTest");
  }

  @Test
  void expect_assertions_mismatch() {
    RuleVerifier.verify("js", "ExpectAssertionsMismatch");
  }
}
