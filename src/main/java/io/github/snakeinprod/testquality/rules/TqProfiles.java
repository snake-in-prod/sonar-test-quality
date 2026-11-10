package io.github.snakeinprod.testquality.rules;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

/**
 * Built-in profiles. SonarQube allows one plugin per built-in profile name and language, so the rules cannot join the
 * "Sonar way" profiles of py, js and ts that the bundled analyzers define: those languages get a built-in "Test Quality"
 * profile with the active rules (to activate them in your own profile, see the README). Dart, which only this plugin
 * defines, gets "Sonar way" as its default profile (SonarQube 26.10 derives "Sonar way core", "extended" and
 * "comprehensive" from it).
 */
public class TqProfiles implements BuiltInQualityProfilesDefinition {

  public static final String SONAR_WAY = "Sonar way";
  public static final String TEST_QUALITY = "Test Quality";

  @Override
  public void define(Context context) {
    for (String language : new String[] {"py", "js", "ts", "dart"}) {
      boolean dart = "dart".equals(language);
      NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(dart ? SONAR_WAY : TEST_QUALITY, language);
      profile.setDefault(dart);
      String repo = RuleCatalog.repository(language);
      for (RuleCatalog.Spec spec : RuleCatalog.family(RuleCatalog.familyOf(language))) {
        if (spec.active()) {
          profile.activateRule(repo, spec.key());
        }
      }
      profile.done();
    }
  }
}
