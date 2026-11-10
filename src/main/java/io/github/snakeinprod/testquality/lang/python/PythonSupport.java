package io.github.snakeinprod.testquality.lang.python;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.util.List;

/** Parsing and test extraction for Python. Not implemented yet. */
public class PythonSupport implements LanguageSupport {

  @Override
  public String family() {
    return "python";
  }

  @Override
  public List<String> languages() {
    return List.of("py");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    throw new UnsupportedOperationException("Python parsing is not implemented yet");
  }

  @Override
  public TestFile extract(ParsedFile file) {
    return TestFile.EMPTY;
  }
}
