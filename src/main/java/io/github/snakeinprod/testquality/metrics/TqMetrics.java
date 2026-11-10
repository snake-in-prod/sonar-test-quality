package io.github.snakeinprod.testquality.metrics;

import java.util.List;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;

/** The tq_* measures. Integer ones are summed from files to directories and the project by {@link TqAggregator}. */
public class TqMetrics implements Metrics {

  public static final String DOMAIN = "Test Quality";

  public static final Metric<Integer> TESTS = intMetric("tq_tests", "Tests found", "Test functions and test cases found");
  public static final Metric<Integer> ASSERTIONS = intMetric("tq_assertions", "Assertions", "Assertions in tests");
  public static final Metric<Integer> TESTS_WITHOUT_ASSERTION = intMetric("tq_tests_without_assertion", "Tests without assertion",
    "Tests with no assertion they can reach (skipped tests not counted)");
  public static final Metric<Double> ASSERTIONS_PER_TEST = new Metric.Builder("tq_assertions_per_test", "Assertions per test",
    Metric.ValueType.FLOAT).setDescription("Assertions divided by tests").setDomain(DOMAIN).setDecimalScale(1)
    .setDirection(Metric.DIRECTION_BETTER).setQualitative(true).create();
  public static final Metric<Integer> MOCKS = intMetric("tq_mocks", "Mocks", "Mocks created or patched in tests");
  public static final Metric<Integer> MOCK_HEAVY_TESTS = intMetric("tq_mock_heavy_tests", "Mock-heavy tests",
    "Tests with more mocks than assertions");
  public static final Metric<Integer> SKIPPED_TESTS = intMetric("tq_skipped_tests", "Skipped tests", "Tests marked skip or xfail");
  public static final Metric<Integer> CORRECTNESS_ISSUES = intMetric("tq_correctness_issues", "Test correctness issues",
    "Issues of the Test Quality rules");

  public static final Metric<Integer> MUTANTS = intMetric("tq_mutants", "Mutants", "Mutants in the mutation report");
  public static final Metric<Integer> MUTANTS_KILLED = intMetric("tq_mutants_killed", "Killed mutants", "Mutants killed (timeouts included)");
  public static final Metric<Integer> MUTANTS_SURVIVED = intMetric("tq_mutants_survived", "Survived mutants", "Mutants no test detected");
  public static final Metric<Integer> MUTANTS_NO_COVERAGE = intMetric("tq_mutants_no_coverage", "Mutants without coverage",
    "Mutants in code no test runs");
  public static final Metric<Integer> MUTANTS_TIMEOUT = intMetric("tq_mutants_timeout", "Timed out mutants", "Mutants that timed out");
  public static final Metric<Double> MUTATION_SCORE = new Metric.Builder("tq_mutation_score", "Mutation score", Metric.ValueType.PERCENT)
    .setDescription("Killed (timeouts included) / mutants that were run").setDomain(DOMAIN).setDirection(Metric.DIRECTION_BETTER)
    .setQualitative(true).setBestValue(100.0).setWorstValue(0.0).create();
  public static final Metric<Integer> TEST_DURATION_P95 = intMetric("tq_test_duration_p95", "Test duration p95 (ms)",
    "95th percentile of test time in the JUnit reports");
  public static final Metric<String> SLOWEST_TESTS = new Metric.Builder("tq_slowest_tests", "Slowest tests", Metric.ValueType.DATA)
    .setDescription("JSON: the 10 slowest tests").setDomain(DOMAIN).setQualitative(false).create();
  public static final Metric<Integer> FLAKY_TESTS = intMetric("tq_flaky_tests", "Flaky tests",
    "Tests that both passed and failed across the reports of one analysis");

  // New code: a file is new code when SonarQube marks it ADDED or CHANGED (against the previous analysis, or the
  // reference branch of a branch or pull request analysis); then it counts whole, else 0.
  public static final Metric<Integer> NEW_TESTS = newMetric("new_tq_tests", "Tests found on new code",
    "Tests in test files added or changed", Metric.DIRECTION_BETTER);
  public static final Metric<Integer> NEW_TESTS_WITHOUT_ASSERTION = newMetric("new_tq_tests_without_assertion",
    "Tests without assertion on new code", "Tests with no assertion in test files added or changed", Metric.DIRECTION_WORST);
  public static final Metric<Integer> NEW_CORRECTNESS_ISSUES = newMetric("new_tq_correctness_issues",
    "Test correctness issues on new code", "Issues of the Test Quality rules in test files added or changed", Metric.DIRECTION_WORST);

  public static final Metric<Integer> SOURCE_FILES = intMetric("tq_source_files", "Source files",
    "Source files with a function or class, for the test reach");
  public static final Metric<Integer> UNTESTED_SOURCE_FILES = new Metric.Builder("tq_untested_source_files", "Untested source files",
    Metric.ValueType.INT).setDescription("Source files no test imports, directly or through one source file").setDomain(DOMAIN)
    .setDirection(Metric.DIRECTION_WORST).setQualitative(true).setBestValue(0.0).create();
  public static final Metric<Integer> NEW_UNTESTED_SOURCE_FILES = newMetric("new_tq_untested_source_files",
    "Untested source files on new code", "Source files added or changed that no test imports", Metric.DIRECTION_WORST);
  public static final Metric<Double> TEST_REACH = new Metric.Builder("tq_test_reach", "Test reach", Metric.ValueType.PERCENT)
    .setDescription("Source files a test imports, directly or through one source file, in %").setDomain(DOMAIN)
    .setDirection(Metric.DIRECTION_BETTER).setQualitative(true).setBestValue(100.0).setWorstValue(0.0).create();

  private static Metric<Integer> intMetric(String key, String name, String description) {
    return new Metric.Builder(key, name, Metric.ValueType.INT).setDescription(description).setDomain(DOMAIN)
      .setDirection(Metric.DIRECTION_WORST).setQualitative(false).create();
  }

  private static Metric<Integer> newMetric(String key, String name, String description, int direction) {
    Metric.Builder b = new Metric.Builder(key, name, Metric.ValueType.INT).setDescription(description).setDomain(DOMAIN)
      .setDirection(direction).setQualitative(direction == Metric.DIRECTION_WORST);
    return (direction == Metric.DIRECTION_WORST ? b.setBestValue(0.0) : b).create();
  }

  /** Integer measures summed from files up. */
  public static final List<Metric<Integer>> SUMMED = List.of(TESTS, ASSERTIONS, TESTS_WITHOUT_ASSERTION, MOCKS, MOCK_HEAVY_TESTS,
    SKIPPED_TESTS, CORRECTNESS_ISSUES, MUTANTS, MUTANTS_KILLED, MUTANTS_SURVIVED, MUTANTS_NO_COVERAGE, MUTANTS_TIMEOUT,
    NEW_TESTS, NEW_TESTS_WITHOUT_ASSERTION, NEW_CORRECTNESS_ISSUES, SOURCE_FILES, UNTESTED_SOURCE_FILES, NEW_UNTESTED_SOURCE_FILES);

  @Override
  @SuppressWarnings("rawtypes")
  public List<Metric> getMetrics() {
    return List.of(TESTS, ASSERTIONS, TESTS_WITHOUT_ASSERTION, ASSERTIONS_PER_TEST, MOCKS, MOCK_HEAVY_TESTS, SKIPPED_TESTS,
      CORRECTNESS_ISSUES, MUTANTS, MUTANTS_KILLED, MUTANTS_SURVIVED, MUTANTS_NO_COVERAGE, MUTANTS_TIMEOUT, MUTATION_SCORE,
      TEST_DURATION_P95, SLOWEST_TESTS, FLAKY_TESTS, NEW_TESTS, NEW_TESTS_WITHOUT_ASSERTION, NEW_CORRECTNESS_ISSUES, SOURCE_FILES,
      UNTESTED_SOURCE_FILES, NEW_UNTESTED_SOURCE_FILES, TEST_REACH);
  }
}
