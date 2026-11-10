package io.github.snakeinprod.testquality.lang.js;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.util.List;

/** Parsing and test extraction for Js. Not implemented yet. */
public class JsSupport implements LanguageSupport {

  @Override
  public String family() {
    return "js";
  }

  @Override
  public List<String> languages() {
    return List.of("js", "ts");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    throw new UnsupportedOperationException("Js parsing is not implemented yet");
  }

  @Override
  public TestFile extract(ParsedFile file) {
    return TestFile.EMPTY;
  }
}
