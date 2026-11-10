package io.github.snakeinprod.testquality.rules;

import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import java.util.function.Function;

/** What a rule sees: the parsed file, its tests, and a way to report. */
public interface RuleContext {

  ParsedFile file();

  TestFile tests();

  /**
   * Reads another file of the project (main code too), parsed by the same language support, or null when it does not
   * exist or does not parse. {@code path} is relative to the base directory. Used by rules that need the code under
   * test ({@code PatchWhereDefined}). Results are cached for the analysis.
   */
  ParsedFile project(String path);

  /** Every project file path of this language, relative to the base directory ("/" separated). */
  java.util.Set<String> projectPaths();

  /** Reports an issue from {@code from} to {@code to} (both included). */
  void report(Token from, Token to, String message);

  default void report(Token at, String message) {
    report(at, at, message);
  }

  /** Shared per-analysis cache for rules that index the project once. */
  <T> T shared(String key, Function<String, T> compute);
}
