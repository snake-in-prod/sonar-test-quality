package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class DartMatcherRulesTest {

  @Test
  void unawaited_expect_later() {
    RuleVerifier.verify("dart", "UnawaitedExpectLater");
  }

  @Test
  void async_matcher_not_awaited() {
    RuleVerifier.verify("dart", "AsyncMatcherNotAwaited");
  }

  @Test
  void trivial_matcher() {
    RuleVerifier.verify("dart", "TrivialMatcher");
  }

  @Test
  void golden_only_test() {
    RuleVerifier.verify("dart", "GoldenOnlyTest");
  }
}
