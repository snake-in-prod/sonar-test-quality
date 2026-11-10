package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class PythonMockRulesTest {

  @Test
  void mock_assertion_typo() {
    RuleVerifier.verify("python", "MockAssertionTypo");
  }

  @Test
  void mock_attribute_as_assertion() {
    RuleVerifier.verify("python", "MockAttributeAsAssertion");
  }

  @Test
  void mock_never_used() {
    RuleVerifier.verify("python", "MockNeverUsed");
  }

  @Test
  void patch_where_defined() {
    RuleVerifier.verify("python", "PatchWhereDefined");
  }
}
