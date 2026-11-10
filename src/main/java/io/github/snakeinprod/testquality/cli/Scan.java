package io.github.snakeinprod.testquality.cli;

import io.github.snakeinprod.testquality.rules.RuleCatalog;
import io.github.snakeinprod.testquality.sensor.Analyzer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs every rule on a directory without SonarQube, for checking rules on real projects:
 * {@code java -cp target/classes io.github.snakeinprod.testquality.cli.Scan <dir>}. Prints one issue per line
 * ({@code path:line:column rule message}), then a summary per rule and the test counts. A file counts as a test file
 * by the default patterns of the plugin settings.
 */
public final class Scan {

  private static final Pattern PY_TEST = Pattern.compile("(^|.*/)(test_[^/]*\\.py|[^/]*_test\\.py|conftest\\.py)$|(^|.*/)tests?/.*\\.py$");
  private static final Pattern JS_TEST = Pattern.compile(".*\\.(test|spec)\\.[cm]?[jt]sx?$|(^|.*/)__tests__/.*\\.[cm]?[jt]sx?$");
  private static final Pattern DART_TEST = Pattern.compile(".*_test\\.dart$|(^|.*/)(test|integration_test)/.*\\.dart$");

  private Scan() {
  }

  public static void main(String[] args) throws IOException {
    Path root = Path.of(args[0]).toAbsolutePath().normalize();
    Map<String, String> languages = new TreeMap<>();
    try (Stream<Path> files = Files.walk(root)) {
      files.filter(Files::isRegularFile).forEach(p -> {
        String rel = root.relativize(p).toString().replace('\\', '/');
        if (rel.contains("node_modules/") || rel.startsWith(".") || rel.contains("/.")) {
          return;
        }
        String lang = language(rel);
        if (lang != null) {
          languages.put(rel, lang);
        }
      });
    }
    Analyzer analyzer = Analyzer.all(new Analyzer.Project() {
      @Override
      public Set<String> paths(String family) {
        Set<String> out = new TreeSet<>();
        languages.forEach((p, l) -> {
          if (RuleCatalog.familyOf(l).equals(family)) {
            out.add(p);
          }
        });
        return out;
      }

      @Override
      public String read(String path) {
        try {
          return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException e) {
          return null;
        }
      }

      @Override
      public String language(String path) {
        return languages.get(path);
      }
    });
    Map<String, Integer> perRule = new TreeMap<>();
    int testFiles = 0;
    int tests = 0;
    int failed = 0;
    for (Map.Entry<String, String> e : languages.entrySet()) {
      if (!isTest(e.getKey(), e.getValue())) {
        continue;
      }
      try {
        String source = Files.readString(root.resolve(e.getKey()), StandardCharsets.UTF_8);
        Analyzer.Result r = analyzer.analyze(e.getKey(), e.getValue(), source);
        testFiles++;
        tests += r.tests().tests().size();
        for (Analyzer.Issue i : r.issues()) {
          System.out.println(i.path() + ":" + i.line() + ":" + i.column() + " " + i.rule() + " " + i.message());
          perRule.merge(i.rule(), 1, Integer::sum);
        }
      } catch (IOException | RuntimeException ex) {
        failed++;
        System.err.println("SKIPPED " + e.getKey() + ": " + ex);
      }
    }
    System.out.println("== " + testFiles + " test files, " + tests + " tests, " + failed + " skipped");
    perRule.forEach((k, v) -> System.out.println("== " + k + " " + v));
  }

  public static String language(String path) {
    if (path.endsWith(".py")) {
      return "py";
    }
    if (path.matches(".*\\.[cm]?jsx?$")) {
      return "js";
    }
    if (path.matches(".*\\.[cm]?tsx?$") && !path.endsWith(".d.ts")) {
      return "ts";
    }
    return path.endsWith(".dart") ? "dart" : null;
  }

  public static boolean isTest(String path, String language) {
    return switch (language) {
      case "py" -> PY_TEST.matcher(path).matches();
      case "js", "ts" -> JS_TEST.matcher(path).matches();
      default -> DART_TEST.matcher(path).matches();
    };
  }
}
