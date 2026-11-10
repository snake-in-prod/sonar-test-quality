package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

class JsAsyncRulesTest {

  @Test
  void unawaited_async_assertion() {
    RuleVerifier.verify("js", "UnawaitedAsyncAssertion");
  }

  @Test
  void assertion_in_unawaited_callback() {
    RuleVerifier.verify("js", "AssertionInUnawaitedCallback");
  }

  @Test
  void done_and_promise() {
    RuleVerifier.verify("js", "DoneAndPromise");
  }

  @Test
  void assertion_in_loop_never_run() {
    RuleVerifier.verify("js", "AssertionInLoopNeverRun");
  }
}
