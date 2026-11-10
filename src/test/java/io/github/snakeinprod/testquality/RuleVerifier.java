package io.github.snakeinprod.testquality;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.testquality.cli.Scan;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleCatalog;
import io.github.snakeinprod.testquality.sensor.Analyzer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks one rule against a fixture. A fixture is {@code src/test/resources/fixtures/<family>/<Key>.<ext>} or a directory
 * {@code fixtures/<family>/<Key>/} holding several files (its test files are checked, the others are project files the
 * rule may read, and after the test files its source files go through the source rules' pass). Every line with a
 * comment containing {@code Noncompliant} must get exactly one issue (or N with {@code Noncompliant N});
 * {@code {{text}}} after it must be part of the message. No other line may get an issue.
 */
public final class RuleVerifier {

  private static final Pattern MARK = Pattern.compile("Noncompliant(?:\\s+(\\d+))?(?:\\s*\\{\\{(.*?)}})?");

  private RuleVerifier() {
  }

  /** Returns, for a directory fixture, whether a test reaches each file counted as a source file. */
  public static Map<String, Boolean> verify(String family, String key) {
    assertThat(RuleCatalog.family(family)).extracting(RuleCatalog.Spec::key).contains(key);
    Path base = Path.of("src/test/resources/fixtures", family);
    Path dir = base.resolve(key);
    Map<String, String> files = new TreeMap<>();
    Path root;
    if (Files.isDirectory(dir)) {
      root = dir;
      walk(dir).forEach(p -> files.put(root.relativize(p).toString().replace('\\', '/'), null));
    } else {
      root = base;
      try (Stream<Path> s = Files.list(base)) {
        s.filter(p -> p.getFileName().toString().matches(Pattern.quote(key) + "\\.[a-z]+")).forEach(p -> files.put(p.getFileName().toString(), null));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    assertThat(files).as("fixture for " + family + "/" + key).isNotEmpty();
    Map<String, String> languages = new TreeMap<>();
    files.keySet().forEach(p -> {
      String l = Scan.language(p);
      if (l != null) {
        languages.put(p, l);
      }
    });
    Analyzer analyzer = new Analyzer(io.github.snakeinprod.testquality.lang.Languages.create(),
      (f, k) -> f.equals(family) && k.equals(key), new Analyzer.Project() {
        @Override
        public Set<String> paths(String fam) {
          Set<String> out = new TreeSet<>();
          languages.forEach((p, l) -> {
            if (RuleCatalog.familyOf(l).equals(fam)) {
              out.add(p);
            }
          });
          return out;
        }

        @Override
        public String read(String path) {
          return Files.isRegularFile(root.resolve(path)) ? content(root.resolve(path)) : null;
        }

        @Override
        public String language(String path) {
          return languages.get(path);
        }
      });
    List<String> problems = new ArrayList<>();
    int checked = 0;
    for (Map.Entry<String, String> e : languages.entrySet()) {
      boolean single = !Files.isDirectory(dir);
      if (!single && !Scan.isTest(e.getKey(), e.getValue())) {
        continue;
      }
      checked++;
      Analyzer.Result r = analyzer.analyze(e.getKey(), e.getValue(), content(root.resolve(e.getKey())));
      compare(e.getKey(), r.file().comments(), r.issues(), problems);
    }
    // source files of a directory fixture, after every test file: the source rules' pass
    Map<String, Boolean> reach = new TreeMap<>();
    for (Map.Entry<String, String> e : Files.isDirectory(dir) ? languages.entrySet() : Set.<Map.Entry<String, String>>of()) {
      if (Scan.isTest(e.getKey(), e.getValue())) {
        continue;
      }
      String source = content(root.resolve(e.getKey()));
      Analyzer.SourceResult r = analyzer.analyzeSource(e.getKey(), e.getValue(), source);
      if (r != null) {
        reach.put(e.getKey(), r.reached());
        compare(e.getKey(), r.file().comments(), r.issues(), problems);
      } else if (MARK.matcher(source).find()) {
        problems.add(e.getKey() + " is not counted as a source file but has a Noncompliant mark");
      }
    }
    assertThat(checked).as("test files checked for " + family + "/" + key).isPositive();
    assertThat(problems).as(family + "/" + key).isEmpty();
    return reach;
  }

  private static void compare(String path, List<Token> comments, List<Analyzer.Issue> issues, List<String> problems) {
    Map<Integer, Integer> expected = new TreeMap<>();
    Map<Integer, String> messages = new TreeMap<>();
    for (Token c : comments) {
      Matcher m = MARK.matcher(c.text());
      if (m.find()) {
        expected.merge(c.line(), m.group(1) == null ? 1 : Integer.parseInt(m.group(1)), Integer::sum);
        if (m.group(2) != null) {
          messages.put(c.line(), m.group(2));
        }
      }
    }
    Map<Integer, List<String>> actual = new TreeMap<>();
    for (Analyzer.Issue i : issues) {
      actual.computeIfAbsent(i.line(), l -> new ArrayList<>()).add(i.message());
    }
    Set<Integer> lines = new TreeSet<>(expected.keySet());
    lines.addAll(actual.keySet());
    for (int line : lines) {
      int want = expected.getOrDefault(line, 0);
      List<String> got = actual.getOrDefault(line, List.of());
      if (want != got.size()) {
        problems.add(path + ":" + line + " expected " + want + " issue(s), got " + got.size() + " " + got);
      } else if (messages.containsKey(line) && got.stream().noneMatch(g -> g.contains(messages.get(line)))) {
        problems.add(path + ":" + line + " message should contain \"" + messages.get(line) + "\", got " + got);
      }
    }
  }

  private static List<Path> walk(Path dir) {
    try (Stream<Path> s = Files.walk(dir)) {
      return s.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String content(Path p) {
    try {
      return Files.readString(p, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
