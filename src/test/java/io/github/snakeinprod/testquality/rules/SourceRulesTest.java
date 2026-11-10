package io.github.snakeinprod.testquality.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.snakeinprod.testquality.RuleVerifier;
import org.junit.jupiter.api.Test;

/** SourceWithoutTest per family; the returned map is the test reach behind tq_untested_source_files. */
class SourceRulesTest {

  @Test
  void python_reach_by_imports() {
    assertThat(RuleVerifier.verify("python", "SourceWithoutTest")).containsOnly(
      // src layout, relative import from cart, star import, test helper, import a.b as c
      entry("src/shop/cart.py", true), entry("src/shop/prices.py", true), entry("src/shop/star.py", true),
      entry("src/shop/builders.py", true), entry("src/shop/discounts.py", true),
      // two levels away, never imported; generated, pb2, migrations and the empty __init__.py are not source files
      entry("src/shop/tax.py", false), entry("src/shop/orphan.py", false), entry("src/shop/small.py", false));
  }

  @Test
  void js_reach_by_imports() {
    assertThat(RuleVerifier.verify("js", "SourceWithoutTest")).containsOnly(
      entry("src/cart.ts", true), entry("src/prices.js", true), entry("src/esm.ts", true), entry("src/legacy.cjs", true),
      entry("src/ui/index.tsx", true), entry("src/ui/Button.tsx", true),
      entry("src/deep.ts", false), entry("src/alias.ts", false), entry("src/orphan.ts", false), entry("src/small.ts", false));
  }

  @Test
  void dart_reach_by_imports() {
    assertThat(RuleVerifier.verify("dart", "SourceWithoutTest")).containsOnly(
      entry("lib/cart.dart", true), entry("lib/src/prices.dart", true), entry("lib/util.dart", true),
      entry("lib/src/tax.dart", false), entry("lib/orphan.dart", false), entry("lib/model.dart", false));
  }
}
