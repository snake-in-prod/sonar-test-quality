package io.github.snakeinprod.testquality;

import io.github.snakeinprod.testquality.lang.dart.DartLanguage;
import io.github.snakeinprod.testquality.metrics.TqAggregator;
import io.github.snakeinprod.testquality.metrics.TqMetrics;
import io.github.snakeinprod.testquality.reports.ReportSensor;
import io.github.snakeinprod.testquality.rules.TqProfiles;
import io.github.snakeinprod.testquality.rules.TqRulesDefinition;
import io.github.snakeinprod.testquality.sensor.TestQualitySensor;
import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;

/** Entry point: languages, rules, profiles, sensors, metrics, the page and the settings. */
public class TestQualityPlugin implements Plugin {

  public static final String CATEGORY = "Test Quality";
  public static final String DART_SUFFIXES = "sonar.testquality.dart.file.suffixes";
  public static final String MUTATION_REPORTS = "sonar.testquality.mutation.reportPaths";
  public static final String JUNIT_REPORTS = "sonar.testquality.junit.reportPaths";
  public static final String DART_REPORTS = "sonar.testquality.dart.reportPaths";
  public static final String PYTHON_TEST_PATTERNS = "sonar.testquality.python.testPatterns";
  public static final String JS_TEST_PATTERNS = "sonar.testquality.js.testPatterns";
  public static final String DART_TEST_PATTERNS = "sonar.testquality.dart.testPatterns";
  public static final String SLOW_TEST_MS = "sonar.testquality.slowTestMs";

  @Override
  public void define(Context context) {
    context.addExtensions(DartLanguage.class, TqRulesDefinition.class, TqProfiles.class, TqMetrics.class, TestQualitySensor.class,
      ReportSensor.class, TqAggregator.class, TestQualityPage.class);
    context.addExtensions(
      prop(DART_SUFFIXES, "Dart file suffixes", "Files the Dart language indexes. Leave empty when another plugin already "
        + "claims .dart files.", ".dart").multiValues(true).build(),
      prop(MUTATION_REPORTS, "Mutation reports", "Paths or globs of mutation-testing-elements JSON reports (Stryker "
        + "mutation.json, or converted mutmut and cosmic-ray results).", null).multiValues(true).build(),
      prop(JUNIT_REPORTS, "JUnit XML reports", "Paths or globs of JUnit XML reports (pytest --junitxml, jest-junit, Vitest, "
        + "Playwright). Several reports of one run let flaky tests show up.", null).multiValues(true).build(),
      prop(DART_REPORTS, "Dart test reports", "Paths or globs of flutter test --machine or dart test --reporter json output.", null)
        .multiValues(true).build(),
      prop(PYTHON_TEST_PATTERNS, "Python test file patterns", "Path patterns of Python test files under sonar.sources.",
        "**/test_*.py,**/*_test.py,**/tests/**/*.py,**/conftest.py").multiValues(true).build(),
      prop(JS_TEST_PATTERNS, "JS/TS test file patterns", "Path patterns of JS/TS test files under sonar.sources.",
        "**/*.test.*,**/*.spec.*,**/__tests__/**").multiValues(true).build(),
      prop(DART_TEST_PATTERNS, "Dart test file patterns", "Path patterns of Dart test files under sonar.sources.",
        "**/*_test.dart,**/test/**/*.dart,**/integration_test/**/*.dart").multiValues(true).build(),
      prop(SLOW_TEST_MS, "Slow test threshold (ms)", "Tests slower than this count as slow on the page.", "1000")
        .type(org.sonar.api.PropertyType.INTEGER).build());
  }

  private static PropertyDefinition.Builder prop(String key, String name, String description, String defaultValue) {
    PropertyDefinition.Builder b = PropertyDefinition.builder(key).name(name).description(description).category(CATEGORY)
      .onConfigScopes(PropertyDefinition.ConfigScope.PROJECT);
    return defaultValue == null ? b : b.defaultValue(defaultValue);
  }
}
