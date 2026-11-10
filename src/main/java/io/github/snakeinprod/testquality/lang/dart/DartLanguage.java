package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.TestQualityPlugin;
import java.util.Arrays;
import org.sonar.api.config.Configuration;
import org.sonar.api.resources.AbstractLanguage;

/**
 * The "dart" language, so that .dart files are indexed and can carry issues. SonarQube Community Build has no Dart
 * language of its own. When another plugin already claims .dart (a community Flutter plugin), set
 * {@code sonar.dart.file.suffixes} to an empty value; two languages cannot share a suffix.
 */
public class DartLanguage extends AbstractLanguage {

  public static final String KEY = "dart";

  private final Configuration config;

  public DartLanguage(Configuration config) {
    super(KEY, "Dart");
    this.config = config;
  }

  @Override
  public String[] getFileSuffixes() {
    String[] suffixes = config.getStringArray(TestQualityPlugin.DART_SUFFIXES);
    return Arrays.stream(suffixes).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }
}
