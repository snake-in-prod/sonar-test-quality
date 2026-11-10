package io.github.snakeinprod.testquality.lang;

import io.github.snakeinprod.testquality.model.Outline;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.util.Set;
import java.util.function.Function;

/** Parsing and test extraction for one language family. One instance per analysis. */
public interface LanguageSupport {

  /** The rule family in {@link io.github.snakeinprod.testquality.rules.RuleCatalog}: "python", "js" or "dart". */
  String family();

  /** SonarQube language keys this support handles: ["py"], ["js", "ts"], ["dart"]. */
  java.util.List<String> languages();

  /**
   * Lexes and builds the trees. Never throws for bad input: unknown characters become {@link
   * io.github.snakeinprod.testquality.parse.TokenKind#OTHER} tokens. May throw only on internal errors; the sensor then
   * skips the file with a warning.
   *
   * @param language the SonarQube language key of the file ("ts" for a .tsx file)
   */
  ParsedFile parse(String path, String language, String source);

  /** Finds the tests of a parsed file. Returns {@link TestFile#EMPTY} when it has none. */
  TestFile extract(ParsedFile file);

  /**
   * The project files a parsed file imports and what it declares.
   *
   * @param paths every project file of this language family, relative to the base directory
   * @param read  content of any file under the base directory (pubspec.yaml too), or null
   */
  Outline outline(ParsedFile file, Set<String> paths, Function<String, String> read);

}
