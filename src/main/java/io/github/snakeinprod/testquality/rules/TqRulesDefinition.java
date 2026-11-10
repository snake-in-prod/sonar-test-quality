package io.github.snakeinprod.testquality.rules;

import java.util.List;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

/** Four repositories: testquality-python (py), testquality-js (js), testquality-ts (ts), testquality-dart (dart). */
public class TqRulesDefinition implements RulesDefinition {

  @Override
  public void define(Context context) {
    repository(context, "py", "python");
    repository(context, "js", "js");
    repository(context, "ts", "js");
    repository(context, "dart", "dart");
  }

  private static void repository(Context context, String language, String family) {
    NewRepository repo = context.createRepository(RuleCatalog.repository(language), language).setName("Test Quality");
    List<RuleCatalog.Spec> specs = RuleCatalog.family(family);
    for (RuleCatalog.Spec spec : specs) {
      repo.createRule(spec.key())
        .setName(spec.name())
        .setHtmlDescription(spec.html())
        .setType(RuleType.BUG)
        .setSeverity(spec.severity())
        .setTags("tests", "test-quality")
        .addDefaultImpact(SoftwareQuality.RELIABILITY, switch (spec.severity()) {
          case "CRITICAL" -> Severity.HIGH;
          case "MINOR" -> Severity.LOW;
          default -> Severity.MEDIUM;
        })
        .setCleanCodeAttribute(CleanCodeAttribute.LOGICAL);
    }
    repo.done();
  }
}
