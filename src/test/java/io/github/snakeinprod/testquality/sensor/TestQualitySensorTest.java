package io.github.snakeinprod.testquality.sensor;

import static io.github.snakeinprod.testquality.sensor.TestQualitySensor.newCodeAnalysis;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestQualitySensorTest {

  @Test
  void new_code_measures_only_in_pull_requests_and_branches() {
    assertThat(newCodeAnalysis("42", null)).isTrue();
    assertThat(newCodeAnalysis("42", "feature/x")).isTrue();
    assertThat(newCodeAnalysis(null, "feature/x")).isTrue();
    // main branch: no new code measures, first analysis included
    assertThat(newCodeAnalysis(null, null)).isFalse();
    assertThat(newCodeAnalysis("", " ")).isFalse();
    assertThat(newCodeAnalysis(null, "main")).isFalse();
    assertThat(newCodeAnalysis(null, "master")).isFalse();
  }
}
