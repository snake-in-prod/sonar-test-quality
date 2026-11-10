package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class DartTesterRulesTest {

  @Test
  void unawaited_tester_action() {
    RuleVerifier.verify("dart", "UnawaitedTesterAction");
  }

  @Test
  void expect_without_pump() {
    RuleVerifier.verify("dart", "ExpectWithoutPump");
  }

  @Test
  void real_delay_in_test() {
    RuleVerifier.verify("dart", "RealDelayInTest");
  }
}
