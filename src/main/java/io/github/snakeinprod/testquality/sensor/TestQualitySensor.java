package io.github.snakeinprod.testquality.sensor;

import io.github.snakeinprod.testquality.TestQualityPlugin;
import io.github.snakeinprod.testquality.lang.Languages;
import io.github.snakeinprod.testquality.metrics.TqMetrics;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.rules.RuleCatalog;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.WildcardPattern;

/**
 * Analyses the test files of py, js, ts and dart: issues of the active Test Quality rules and the code measures.
 * A file is a test file when SonarQube indexes it as one ({@code sonar.tests}) or its path matches the language's
 * test file patterns.
 */
public class TestQualitySensor implements Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(TestQualitySensor.class);
  private static final List<String> LANGUAGES = List.of("py", "js", "ts", "dart");

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Test Quality").onlyOnLanguages(LANGUAGES.toArray(String[]::new));
  }

  @Override
  public void execute(SensorContext context) {
    FileSystem fs = context.fileSystem();
    Map<String, InputFile> byPath = new HashMap<>();
    for (InputFile f : fs.inputFiles(fs.predicates().hasLanguages(LANGUAGES))) {
      byPath.put(f.relativePath(), f);
    }
    Analyzer analyzer = new Analyzer(Languages.create(), (family, key) -> isActive(context, family, key), new Analyzer.Project() {
      @Override
      public Set<String> paths(String family) {
        Set<String> out = new TreeSet<>();
        byPath.forEach((p, f) -> {
          if (RuleCatalog.familyOf(f.language()).equals(family)) {
            out.add(p);
          }
        });
        return out;
      }

      @Override
      public String read(String path) {
        InputFile f = byPath.get(path);
        try {
          return f == null ? null : f.contents();
        } catch (IOException e) {
          return null;
        }
      }

      @Override
      public String language(String path) {
        InputFile f = byPath.get(path);
        return f == null ? null : f.language();
      }
    });
    Map<String, WildcardPattern[]> patterns = Map.of(
      "py", WildcardPattern.create(context.config().getStringArray(TestQualityPlugin.PYTHON_TEST_PATTERNS)),
      "js", WildcardPattern.create(context.config().getStringArray(TestQualityPlugin.JS_TEST_PATTERNS)),
      "ts", WildcardPattern.create(context.config().getStringArray(TestQualityPlugin.JS_TEST_PATTERNS)),
      "dart", WildcardPattern.create(context.config().getStringArray(TestQualityPlugin.DART_TEST_PATTERNS)));
    int analysed = 0;
    int failed = 0;
    for (InputFile f : byPath.values().stream().sorted((a, b) -> a.relativePath().compareTo(b.relativePath())).toList()) {
      boolean test = f.type() == InputFile.Type.TEST || WildcardPattern.match(patterns.get(f.language()), f.relativePath());
      if (!test) {
        continue;
      }
      try {
        Analyzer.Result r = analyzer.analyze(f.relativePath(), f.language(), f.contents());
        save(context, f, r);
        analysed++;
      } catch (IOException | RuntimeException e) {
        failed++;
        LOG.warn("Test Quality: skipped {}: {}", f.relativePath(), e.toString());
      }
    }
    LOG.info("Test Quality: {} test files analysed, {} skipped", analysed, failed);
  }

  private static boolean isActive(SensorContext context, String family, String key) {
    for (String language : LANGUAGES) {
      if (RuleCatalog.familyOf(language).equals(family)
        && context.activeRules().find(RuleKey.of(RuleCatalog.repository(language), key)) != null) {
        return true;
      }
    }
    return false;
  }

  private static void save(SensorContext context, InputFile f, Analyzer.Result r) {
    String repo = RuleCatalog.repository(f.language());
    int issues = 0;
    for (Analyzer.Issue i : r.issues()) {
      RuleKey key = RuleKey.of(repo, i.rule());
      if (context.activeRules().find(key) == null) {
        continue;
      }
      NewIssue issue = context.newIssue().forRule(key);
      NewIssueLocation at = issue.newLocation().on(f).at(range(f, i)).message(i.message());
      issue.at(at).save();
      issues++;
    }
    TestFile t = r.tests();
    if (t.tests().isEmpty() && issues == 0) {
      return;
    }
    measure(context, f, TqMetrics.TESTS, t.tests().size());
    measure(context, f, TqMetrics.ASSERTIONS, t.assertions());
    measure(context, f, TqMetrics.TESTS_WITHOUT_ASSERTION, (int) t.withoutAssertion());
    measure(context, f, TqMetrics.MOCKS, t.mocks());
    measure(context, f, TqMetrics.MOCK_HEAVY_TESTS, (int) t.mockHeavy());
    measure(context, f, TqMetrics.SKIPPED_TESTS, (int) t.skipped());
    measure(context, f, TqMetrics.CORRECTNESS_ISSUES, issues);
  }

  private static TextRange range(InputFile f, Analyzer.Issue i) {
    try {
      int endLine = Math.min(i.endLine(), f.lines());
      int endColumn = i.endLine() > f.lines() ? f.selectLine(f.lines()).end().lineOffset() : i.endColumn();
      TextRange r = f.newRange(i.line(), i.column(), endLine, endColumn);
      return r.start().equals(r.end()) ? f.selectLine(i.line()) : r;
    } catch (IllegalArgumentException e) {
      return f.selectLine(Math.max(1, Math.min(i.line(), f.lines())));
    }
  }

  private static void measure(SensorContext context, InputFile f, org.sonar.api.measures.Metric<Integer> metric, int value) {
    context.<Integer>newMeasure().on(f).forMetric(metric).withValue(value).save();
  }
}
