package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.util.List;

/** Parsing and test extraction for Dart. Not implemented yet. */
public class DartSupport implements LanguageSupport {

  @Override
  public String family() {
    return "dart";
  }

  @Override
  public List<String> languages() {
    return List.of("dart");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    throw new UnsupportedOperationException("Dart parsing is not implemented yet");
  }

  @Override
  public TestFile extract(ParsedFile file) {
    return TestFile.EMPTY;
  }
}
