package io.github.snakeinprod.testquality.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportsTest {

  private static InputStream resource(String name) {
    return ReportsTest.class.getResourceAsStream("/reports/" + name);
  }

  private static Reader reader(String name) {
    return new InputStreamReader(resource(name), StandardCharsets.UTF_8);
  }

  @Test
  void mutation_report_v1_counts_per_file() throws IOException {
    try (Reader r = reader("mutation-v1.json")) {
      Map<String, MutationReport.Counts> files = MutationReport.parse(r);
      // Killed, Survived, Timeout count; Ignored does not
      assertThat(files).containsEntry("src/calc.py", new MutationReport.Counts(3, 2, 1, 0, 1));
      // CompileError does not count
      assertThat(files).containsEntry("src/util/strings.py", new MutationReport.Counts(1, 0, 0, 1, 0));
    }
  }

  @Test
  void mutation_report_v2_with_absolute_paths() throws IOException {
    try (Reader r = reader("mutation-v2.json")) {
      // RuntimeError and Pending do not count
      assertThat(MutationReport.parse(r)).containsExactly(
        Map.entry("/home/ci/work/app/web/src/cart.ts", new MutationReport.Counts(2, 1, 1, 0, 0)));
    }
  }

  @Test
  void mutation_report_without_files_is_rejected() {
    assertThatThrownBy(() -> MutationReport.parse(new StringReader("{\"schemaVersion\":\"2\"}")))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void report_paths_resolve_to_indexed_files(@TempDir Path base) {
    Set<String> indexed = Set.of("src/calc.py", "src/util/strings.py", "web/src/cart.ts", "a/x.py", "b/x.py");
    assertThat(ReportSensor.resolve("src/calc.py", base, indexed)).isEqualTo("src/calc.py");
    assertThat(ReportSensor.resolve("./src/../src/calc.py", base, indexed)).isEqualTo("src/calc.py");
    assertThat(ReportSensor.resolve(base.resolve("src/util/strings.py").toString(), base, indexed)).isEqualTo("src/util/strings.py");
    // absolute path of another machine: longest suffix
    assertThat(ReportSensor.resolve("/home/ci/work/app/web/src/cart.ts", base, indexed)).isEqualTo("web/src/cart.ts");
    assertThat(ReportSensor.resolve("C:\\ci\\app\\web\\src\\cart.ts", base, indexed)).isEqualTo("web/src/cart.ts");
    // path relative to a sub project
    assertThat(ReportSensor.resolve("util/strings.py", base, indexed)).isEqualTo("src/util/strings.py");
    assertThat(ReportSensor.resolve("src/missing.py", base, indexed)).isNull();
    // two equally good matches
    assertThat(ReportSensor.resolve("c/x.py", base, indexed)).isNull();
  }

  @Test
  void flaky_test_across_two_junit_files_and_skipped_test() throws IOException {
    Results results = new Results();
    for (String f : List.of("pytest-run1.xml", "pytest-run2.xml")) {
      try (InputStream in = resource(f)) {
        JUnitReport.parse(in, results);
      }
    }
    assertThat(results.flaky()).containsExactly("tests.test_net > test_retry_backoff");
    // skipped test_proxy has no duration; a test seen twice keeps its longest time
    assertThat(results.durations()).containsOnly(
      Map.entry("tests.test_calc > test_add", 4L),
      Map.entry("tests.test_calc > test_add_negative", 120L),
      Map.entry("tests.test_net > test_retry_backoff", 1900L));
    assertThat(results.p95()).isEqualTo(1900);
    assertThat(results.slowestJson()).isEqualTo("[{\"name\":\"tests.test_net > test_retry_backoff\",\"ms\":1900},"
      + "{\"name\":\"tests.test_calc > test_add_negative\",\"ms\":120},{\"name\":\"tests.test_calc > test_add\",\"ms\":4}]");
  }

  @Test
  void jest_junit_names_and_failures() throws IOException {
    Results results = new Results();
    try (InputStream in = resource("jest-junit.xml")) {
      JUnitReport.parse(in, results);
    }
    assertThat(results.durations()).containsOnly(
      Map.entry("Cart total sums prices", 4L),
      Map.entry("Cart total applies discount", 512L),
      Map.entry("Cart > renders empty state", 30L));
    assertThat(results.flaky()).isEmpty();
  }

  @Test
  void junit_with_doctype_is_refused() {
    assertThatThrownBy(() -> {
      try (InputStream in = resource("xxe.xml")) {
        JUnitReport.parse(in, new Results());
      }
    }).isInstanceOf(IOException.class);
  }

  @Test
  void dart_json_durations_hidden_skipped_and_flaky() throws IOException {
    Results results = new Results();
    for (String f : List.of("flutter-run1.jsonl", "flutter-run2.jsonl")) {
      try (Reader r = reader(f)) {
        DartTestReport.parse(r, results);
      }
    }
    assertThat(results.durations()).containsOnly(
      Map.entry("test/cart_test.dart > Cart adds an item", 40L),
      Map.entry("test/cart_test.dart > Cart syncs with server", 2200L));
    assertThat(results.flaky()).containsExactly("test/cart_test.dart > Cart syncs with server");
  }

  @Test
  void p95_nearest_rank() {
    Results empty = new Results();
    assertThat(empty.p95()).isZero();
    assertThat(empty.slowestJson()).isEqualTo("[]");

    Results one = new Results();
    one.add("a", 7, Results.Outcome.PASSED);
    assertThat(one.p95()).isEqualTo(7);

    Results twenty = new Results();
    for (int i = 1; i <= 20; i++) {
      twenty.add("t" + i, i * 10L, Results.Outcome.PASSED);
    }
    assertThat(twenty.p95()).isEqualTo(190); // rank ceil(0.95 * 20) = 19
    assertThat(twenty.slowest(10)).hasSize(10).first().isEqualTo(Map.entry("t20", 200L));

    Results hundredOne = new Results();
    for (int i = 1; i <= 101; i++) {
      hundredOne.add("t" + i, i, Results.Outcome.FAILED);
    }
    assertThat(hundredOne.p95()).isEqualTo(96); // rank ceil(95.95) = 96
  }

  @Test
  void report_globs_and_paths(@TempDir Path base) throws IOException {
    Files.createDirectories(base.resolve("reports/a"));
    Files.createDirectories(base.resolve("reports/b/deep"));
    Files.writeString(base.resolve("reports/a/junit-1.xml"), "<testsuites/>");
    Files.writeString(base.resolve("reports/b/deep/junit-2.xml"), "<testsuites/>");
    Files.writeString(base.resolve("reports/b/notes.txt"), "x");

    assertThat(ReportSensor.find(base, new String[] {"reports/**/junit-*.xml"}))
      .containsExactlyInAnyOrder(base.resolve("reports/a/junit-1.xml"), base.resolve("reports/b/deep/junit-2.xml"));
    assertThat(ReportSensor.find(base, new String[] {base.resolve("reports/a").toString() + "/*.xml"}))
      .containsExactly(base.resolve("reports/a/junit-1.xml"));
    assertThat(ReportSensor.find(base, new String[] {"reports/a/junit-1.xml", " reports/a/junit-1.xml ", ""}))
      .containsExactly(base.resolve("reports/a/junit-1.xml"));
    assertThat(ReportSensor.find(base, new String[] {"missing/*.xml", "missing.xml"})).isEmpty();
  }
}
