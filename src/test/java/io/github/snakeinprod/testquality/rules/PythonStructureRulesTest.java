package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class PythonStructureRulesTest {

  @Test
  void shadowed_test() {
    RuleVerifier.verify("python", "ShadowedTest");
  }

  @Test
  void undiscovered_test() {
    RuleVerifier.verify("python", "UndiscoveredTest");
  }

  @Test
  void return_instead_of_assert() {
    RuleVerifier.verify("python", "ReturnInsteadOfAssert");
  }

  @Test
  void comparison_without_assert() {
    RuleVerifier.verify("python", "ComparisonWithoutAssert");
  }

  @Test
  void parametrize_mismatch() {
    RuleVerifier.verify("python", "ParametrizeMismatch");
  }
}
