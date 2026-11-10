package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class DartMockRulesTest {

  @Test
  void verify_without_call() {
    RuleVerifier.verify("dart", "VerifyWithoutCall");
  }

  @Test
  void stub_never_used() {
    RuleVerifier.verify("dart", "StubNeverUsed");
  }

  @Test
  void mock_never_verified() {
    RuleVerifier.verify("dart", "MockNeverVerified");
  }

  @Test
  void duplicate_test_name() {
    RuleVerifier.verify("dart", "DuplicateTestName");
  }

  @Test
  void skip_without_reason() {
    RuleVerifier.verify("dart", "SkipWithoutReason");
  }
}
