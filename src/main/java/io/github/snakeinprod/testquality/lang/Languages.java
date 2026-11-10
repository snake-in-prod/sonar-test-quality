package io.github.snakeinprod.testquality.lang;

import io.github.snakeinprod.testquality.lang.dart.DartSupport;
import io.github.snakeinprod.testquality.lang.js.JsSupport;
import io.github.snakeinprod.testquality.lang.python.PythonSupport;
import java.util.List;

/** The language supports, one fresh set per analysis. */
public final class Languages {

  private Languages() {
  }

  public static List<LanguageSupport> create() {
    return List.of(new PythonSupport(), new JsSupport(), new DartSupport());
  }

  /** The support handling a SonarQube language key, or null. */
  public static LanguageSupport forLanguage(List<LanguageSupport> supports, String language) {
    return supports.stream().filter(s -> s.languages().contains(language)).findFirst().orElse(null);
  }
}
