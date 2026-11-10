package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class JsStructureRulesTest {

  @Test
  void shadowed_test() {
    RuleVerifier.verify("js", "ShadowedTest");
  }

  @Test
  void sync_query_for_async_ui() {
    RuleVerifier.verify("js", "SyncQueryForAsyncUi");
  }

  @Test
  void wait_for_timeout() {
    RuleVerifier.verify("js", "WaitForTimeout");
  }

  @Test
  void mock_never_asserted() {
    RuleVerifier.verify("js", "MockNeverAsserted");
  }
}
