package io.github.snakeinprod.testquality;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.parse.TreeBuilder;
import io.github.snakeinprod.testquality.rules.RuleCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginTest {

  @Test
  void catalog_keys_are_unique_per_family() {
    for (String family : List.of("python", "js", "dart")) {
      List<String> keys = RuleCatalog.family(family).stream().map(RuleCatalog.Spec::key).toList();
      assertThat(new HashSet<>(keys)).hasSameSizeAs(keys);
      assertThat(keys).isNotEmpty();
    }
    assertThat(RuleCatalog.ALL).allSatisfy(s -> assertThat(s.factory().get()).isNotNull());
  }

  @Test
  void chains_follow_names_calls_and_accessors() {
    // expect ( x ) . resolves . toEqual ( 1 )
    List<Token> t = new ArrayList<>();
    String[] parts = {"await", "expect", "(", "x", ")", ".", "resolves", ".", "toEqual", "(", "1", ")"};
    int col = 0;
    for (String p : parts) {
      TokenKind k = p.matches("[A-Za-z]+") ? TokenKind.IDENT : p.matches("\\d+") ? TokenKind.NUMBER : TokenKind.OP;
      t.add(new Token(k, p, 1, col, 1, col + p.length()));
      col += p.length() + 1;
    }
    Node.Group root = TreeBuilder.build(t);
    List<Chain> chains = Chains.in(root);
    Chain c = chains.stream().filter(ch -> ch.firstName().equals("expect")).findFirst().orElseThrow();
    assertThat(c.path()).isEqualTo("expect.resolves.toEqual");
    assertThat(c.calls()).hasSize(2);
    assertThat(c.precededBy("await")).isTrue();
    assertThat(c.endsWithCall()).isTrue();
    assertThat(chains).extracting(Chain::firstName).doesNotContain("await");
  }
}
