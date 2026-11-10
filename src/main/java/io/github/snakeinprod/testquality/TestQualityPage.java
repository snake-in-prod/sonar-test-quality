package io.github.snakeinprod.testquality;

import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

/** The project page "Test Quality" ({@code static/testquality.js}). */
public class TestQualityPage implements PageDefinition {

  @Override
  public void define(Context context) {
    context.addPage(Page.builder("testquality/testquality").setName("Test Quality").setScope(Page.Scope.COMPONENT)
      .setComponentQualifiers(Page.Qualifier.PROJECT).build());
  }
}
