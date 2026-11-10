package io.github.snakeinprod.testquality.reports;

import io.github.snakeinprod.testquality.TestQualityPlugin;
import io.github.snakeinprod.testquality.metrics.TqMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.measures.Metric;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.api.utils.WildcardPattern;

/**
 * Imports mutation (mutation-testing-elements JSON), JUnit XML and Dart test JSON reports into the tq_* report
 * measures. Runs once per project, only when one of the report settings is set. A report that cannot be read is
 * logged and skipped; the analysis never fails because of a report.
 */
public class ReportSensor implements ProjectSensor {

  private static final Logger LOG = LoggerFactory.getLogger(ReportSensor.class);
  private static final List<String> SETTINGS = List.of(TestQualityPlugin.MUTATION_REPORTS, TestQualityPlugin.JUNIT_REPORTS,
    TestQualityPlugin.DART_REPORTS);

  @FunctionalInterface
  private interface Parser {
    void parse(Path report, Results into) throws IOException;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Test Quality reports").onlyWhenConfiguration(c -> SETTINGS.stream().anyMatch(c::hasKey));
  }

  @Override
  public void execute(SensorContext context) {
    Path base = context.fileSystem().baseDir().toPath().toAbsolutePath().normalize();
    importMutation(context, base, find(base, context.config().getStringArray(TestQualityPlugin.MUTATION_REPORTS)));

    Results results = new Results();
    int read = readAll(find(base, context.config().getStringArray(TestQualityPlugin.JUNIT_REPORTS)), results, (p, r) -> {
      try (InputStream in = Files.newInputStream(p)) {
        JUnitReport.parse(in, r);
      }
    });
    read += readAll(find(base, context.config().getStringArray(TestQualityPlugin.DART_REPORTS)), results, (p, r) -> {
      try (Reader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
        DartTestReport.parse(in, r);
      }
    });
    if (read == 0) {
      return;
    }
    if (results.hasDurations()) {
      projectMeasure(context, TqMetrics.TEST_DURATION_P95, (int) Math.min(Integer.MAX_VALUE, results.p95()));
      projectMeasure(context, TqMetrics.SLOWEST_TESTS, results.slowestJson());
    }
    projectMeasure(context, TqMetrics.FLAKY_TESTS, results.flaky().size());
    LOG.info("Test Quality: {} test reports read, {} tests timed, {} flaky", read, results.durations().size(), results.flaky().size());
  }

  private static int readAll(List<Path> reports, Results results, Parser parser) {
    int read = 0;
    for (Path p : reports) {
      try {
        parser.parse(p, results);
        read++;
      } catch (IOException | RuntimeException e) {
        LOG.warn("Test Quality: cannot read test report {}: {}", p, e.toString());
      }
    }
    return read;
  }

  private static void importMutation(SensorContext context, Path base, List<Path> reports) {
    if (reports.isEmpty()) {
      return;
    }
    FileSystem fs = context.fileSystem();
    Map<String, InputFile> indexed = new HashMap<>();
    for (InputFile f : fs.inputFiles(fs.predicates().all())) {
      indexed.put(slashes(base.relativize(f.path().toAbsolutePath().normalize()).toString()), f);
    }
    Map<InputFile, MutationReport.Counts> counts = new LinkedHashMap<>();
    int unknown = 0;
    for (Path report : reports) {
      Map<String, MutationReport.Counts> files;
      try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
        files = MutationReport.parse(in);
      } catch (IOException | RuntimeException e) {
        LOG.warn("Test Quality: cannot read mutation report {}: {}", report, e.toString());
        continue;
      }
      for (Map.Entry<String, MutationReport.Counts> e : files.entrySet()) {
        String path = resolve(e.getKey(), base, indexed.keySet());
        if (path == null) {
          unknown++;
          LOG.debug("Test Quality: file of mutation report {} not indexed: {}", report, e.getKey());
          continue;
        }
        counts.merge(indexed.get(path), e.getValue(), MutationReport.Counts::plus);
      }
    }
    counts.forEach((f, c) -> {
      fileMeasure(context, f, TqMetrics.MUTANTS, c.mutants());
      fileMeasure(context, f, TqMetrics.MUTANTS_KILLED, c.killed());
      fileMeasure(context, f, TqMetrics.MUTANTS_SURVIVED, c.survived());
      fileMeasure(context, f, TqMetrics.MUTANTS_NO_COVERAGE, c.noCoverage());
      fileMeasure(context, f, TqMetrics.MUTANTS_TIMEOUT, c.timeout());
    });
    LOG.info("Test Quality: mutation results imported for {} files, {} report files not found in the analysis", counts.size(), unknown);
  }

  /**
   * The indexed path (relative to {@code base}, with {@code /}) a report path points to: the absolute path, the path
   * relative to {@code base}, or else the indexed path sharing the longest run of trailing segments with it (at least
   * the file name). Null when nothing matches or two indexed paths match equally well.
   */
  static String resolve(String reportPath, Path base, Collection<String> indexed) {
    String p = slashes(reportPath.trim());
    try {
      Path direct = base.resolve(p).toAbsolutePath().normalize();
      if (direct.startsWith(base)) {
        String rel = slashes(base.relativize(direct).toString());
        if (indexed.contains(rel)) {
          return rel;
        }
      }
    } catch (InvalidPathException e) {
      // not a path on this system (for example a Windows path on Linux): try the suffix match
    }
    String[] segments = p.split("/");
    String best = null;
    int bestLength = 0;
    boolean tie = false;
    for (String candidate : indexed) {
      String[] c = candidate.split("/");
      int n = 0;
      while (n < c.length && n < segments.length && c[c.length - 1 - n].equals(segments[segments.length - 1 - n])) {
        n++;
      }
      if (n > bestLength) {
        best = candidate;
        bestLength = n;
        tie = false;
      } else if (n == bestLength && n > 0) {
        tie = true;
      }
    }
    return tie ? null : best;
  }

  /**
   * Report files for the configured paths: plain paths (relative to {@code base} or absolute) and globs ({@code *},
   * {@code **}, {@code ?}). A path that matches nothing is logged.
   */
  static List<Path> find(Path base, String[] patterns) {
    Set<Path> out = new LinkedHashSet<>();
    for (String raw : patterns) {
      String p = slashes(raw.trim());
      if (p.isEmpty()) {
        continue;
      }
      int before = out.size();
      try {
        int glob = indexOfGlob(p);
        if (glob < 0) {
          Path f = base.resolve(p).normalize();
          if (Files.isRegularFile(f)) {
            out.add(f);
          }
        } else {
          int slash = p.lastIndexOf('/', glob);
          Path root = slash < 0 ? base : base.resolve(slash == 0 ? "/" : p.substring(0, slash)).normalize();
          WildcardPattern pattern = WildcardPattern.create(p.substring(slash + 1));
          if (Files.isDirectory(root)) {
            // ponytail: walks the whole tree under the glob's fixed prefix; narrow the pattern if that is slow
            try (Stream<Path> files = Files.walk(root)) {
              files.filter(Files::isRegularFile).filter(f -> pattern.match(slashes(root.relativize(f).toString()))).sorted()
                .forEach(out::add);
            }
          }
        }
      } catch (IOException | InvalidPathException | java.io.UncheckedIOException e) {
        LOG.warn("Test Quality: cannot list reports for {}: {}", raw, e.toString());
      }
      if (out.size() == before) {
        LOG.warn("Test Quality: no report found for {}", raw);
      }
    }
    return new ArrayList<>(out);
  }

  private static int indexOfGlob(String p) {
    for (int i = 0; i < p.length(); i++) {
      if (p.charAt(i) == '*' || p.charAt(i) == '?') {
        return i;
      }
    }
    return -1;
  }

  private static String slashes(String path) {
    return path.replace('\\', '/');
  }

  private static void fileMeasure(SensorContext context, InputFile f, Metric<Integer> metric, int value) {
    context.<Integer>newMeasure().on(f).forMetric(metric).withValue(value).save();
  }

  private static <T extends java.io.Serializable> void projectMeasure(SensorContext context, Metric<T> metric, T value) {
    context.<T>newMeasure().on(context.project()).forMetric(metric).withValue(value).save();
  }
}
