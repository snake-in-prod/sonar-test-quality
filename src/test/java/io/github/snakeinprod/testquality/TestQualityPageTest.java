package io.github.snakeinprod.testquality;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;

class TestQualityPageTest {

  @Test
  void page_script_registers_the_page_key() throws IOException {
    Context context = new Context();
    new TestQualityPage().define(context);
    String key = context.getPages().stream().map(Page::getKey).findFirst().orElseThrow();

    // SonarQube serves the page from static/<plugin key>/<page>.js -> static/<page>.js in the jar
    try (InputStream in = getClass().getResourceAsStream("/static/" + key.substring(key.indexOf('/') + 1) + ".js")) {
      assertThat(in).isNotNull();
      String js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(js).contains("window.registerExtension('" + key + "'");
      assertThat(js.chars().filter(c -> c == '{').count()).isEqualTo(js.chars().filter(c -> c == '}').count());
      assertThat(js.chars().filter(c -> c == '(').count()).isEqualTo(js.chars().filter(c -> c == ')').count());
      // no external resources: the SVG namespace is the only URL; charts are built with DOM calls, never innerHTML
      assertThat(js.replace("'http://www.w3.org/2000/svg'", "")).doesNotContain("http://", "https://");
      assertThat(js).contains("createElementNS(").doesNotContain("innerHTML", "eval(");
      // the rules facet is sticky (it ignores the rules filter): keep only this plugin's rules, not java:/css: rules
      assertThat(js).contains("v.count > 0 && names[v.val]");
      // rules of different languages share names: each bar carries its language tag
      assertThat(js).contains("tag: LANGS[v.val.split(':')[0]]", "'testquality-dart': 'Dart'");
    }
  }
}
