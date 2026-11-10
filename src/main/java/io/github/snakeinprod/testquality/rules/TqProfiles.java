package io.github.snakeinprod.testquality.rules;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

/**
 * Adds the active rules to the built-in "Sonar way" profiles of py, js and ts (the bundled analyzers define them; the
 * rules of every plugin that contributes to a built-in profile of the same name are merged), and defines "Sonar way"
 * for dart as its default.
 */
public class TqProfiles implements BuiltInQualityProfilesDefinition {

  public static final String SONAR_WAY = "Sonar way";

  @Override
  public void define(Context context) {
    for (String language : new String[] {"py", "js", "ts", "dart"}) {
      NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(SONAR_WAY, language);
      if ("dart".equals(language)) {
        profile.setDefault(true);
      }
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
