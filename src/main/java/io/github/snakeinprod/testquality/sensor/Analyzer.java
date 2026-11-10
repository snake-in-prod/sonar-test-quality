package io.github.snakeinprod.testquality.sensor;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.lang.Languages;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleCatalog;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Runs parsing, test extraction and the active rules on one file at a time. Shared by the sensor, the command line
 * scanner and the tests, so that all three see the same results.
 */
public final class Analyzer {

  /** An issue; lines 1-based, columns 0-based, {@code endColumn} exclusive. */
  public record Issue(String path, String language, String rule, int line, int column, int endLine, int endColumn, String message) {
  }

  public record Result(String path, String language, ParsedFile file, TestFile tests, List<Issue> issues) {
  }

  /** The project's other files, for rules that read the code under test. */
  public interface Project {
    /** Paths relative to the base directory of every file in this language family. */
    Set<String> paths(String family);

    /** Content of a project file, or null. */
    String read(String path);

    /** SonarQube language key of a project file, or null. */
    String language(String path);
  }

  private final List<LanguageSupport> supports;
  private final Project project;
  private final Map<String, Map<String, TestRule>> rules = new HashMap<>();
  private final Map<String, ParsedFile> parsed = new HashMap<>();
  private final Set<String> unparsable = new HashSet<>();
  private final Map<String, Object> shared = new HashMap<>();

  /**
   * @param active (family, rule key) is active in the profile
   */
  public Analyzer(List<LanguageSupport> supports, BiPredicate<String, String> active, Project project) {
    this.supports = supports;
    this.project = project;
    for (RuleCatalog.Spec spec : RuleCatalog.ALL) {
      if (active.test(spec.family(), spec.key())) {
        rules.computeIfAbsent(spec.family(), f -> new LinkedHashMap<>()).put(spec.key(), spec.factory().get());
      }
    }
  }

  public static Analyzer all(Project project) {
    return new Analyzer(Languages.create(), (f, k) -> true, project);
  }

  /** Analyses one file. Throws only on an internal error of a parser or rule; callers skip the file then. */
  public Result analyze(String path, String language, String source) {
    LanguageSupport support = Languages.forLanguage(supports, language);
    if (support == null) {
      return new Result(path, language, null, TestFile.EMPTY, List.of());
    }
    ParsedFile file = support.parse(path, language, source);
    TestFile tests = support.extract(file);
    List<Issue> issues = new ArrayList<>();
    Set<Integer> nosonar = new HashSet<>();
    for (Token c : file.comments()) {
      if (c.text().contains("NOSONAR")) {
        nosonar.add(c.line());
      }
    }
    for (Map.Entry<String, TestRule> e : rules.getOrDefault(support.family(), Map.of()).entrySet()) {
      String key = e.getKey();
      e.getValue().check(new Context(support, file, tests, key, issues, nosonar));
    }
    issues.sort((a, b) -> a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column()));
    return new Result(path, language, file, tests, List.copyOf(issues));
  }

  private final class Context implements RuleContext {
    private final LanguageSupport support;
    private final ParsedFile file;
    private final TestFile tests;
    private final String rule;
    private final List<Issue> issues;
    private final Set<Integer> nosonar;

    Context(LanguageSupport support, ParsedFile file, TestFile tests, String rule, List<Issue> issues, Set<Integer> nosonar) {
      this.support = support;
      this.file = file;
      this.tests = tests;
      this.rule = rule;
      this.issues = issues;
      this.nosonar = nosonar;
    }

    @Override
    public ParsedFile file() {
      return file;
    }

    @Override
    public TestFile tests() {
      return tests;
    }

    @Override
    public ParsedFile project(String path) {
      if (parsed.containsKey(path)) {
        return parsed.get(path);
      }
      if (unparsable.contains(path)) {
        return null;
      }
      String source = project.read(path);
      String language = project.language(path);
      if (source == null || language == null || !support.languages().contains(language)) {
        unparsable.add(path);
        return null;
      }
      try {
        ParsedFile p = support.parse(path, language, source);
        parsed.put(path, p);
        return p;
      } catch (RuntimeException e) {
        unparsable.add(path);
        return null;
      }
    }

    @Override
    public Set<String> projectPaths() {
      return project.paths(support.family());
    }

    @Override
    public void report(Token from, Token to, String message) {
      if (!nosonar.contains(from.line())) {
        issues.add(new Issue(file.path(), file.language(), rule, from.line(), from.column(), to.endLine(), to.endColumn(), message));
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T shared(String key, Function<String, T> compute) {
      return (T) shared.computeIfAbsent(support.family() + ":" + key, compute);
    }
  }
}
