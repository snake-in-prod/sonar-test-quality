package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class PythonFlowRulesTest {

  @Test
  void assertion_inside_raises() {
    RuleVerifier.verify("python", "AssertionInsideRaises");
  }

  @Test
  void raises_too_broad() {
    RuleVerifier.verify("python", "RaisesTooBroad");
  }

  @Test
  void except_swallows_assertion() {
    RuleVerifier.verify("python", "ExceptSwallowsAssertion");
  }

  @Test
  void sleep_in_test() {
    RuleVerifier.verify("python", "SleepInTest");
  }

  @Test
  void unseeded_random() {
    RuleVerifier.verify("python", "UnseededRandom");
  }
}
