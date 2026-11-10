package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A second test with the same name replaces the first one, which then never runs. */
public class ShadowedTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m != null) {
      scope(ctx, m.statements());
    }
  }

  /** The defs and classes directly in one module or class body; those under if / try are left alone (alternatives). */
  private static void scope(RuleContext ctx, List<PyStatement> statements) {
    Map<String, PyStatement> seen = new HashMap<>();
    for (PyStatement s : statements) {
      String kw = s.keyword();
      String name = s.name();
      if (kw.equals("class")) {
        scope(ctx, s.body());
      }
      if (name == null || !(kw.equals("def") && name.startsWith("test") || kw.equals("class") && name.startsWith("Test"))) {
        continue;
      }
      PyStatement first = seen.put(name, s);
      if (first != null && first.keyword().equals(kw) && !usedBetween(ctx, first, s)) {
        ctx.report(s.nameToken(), "\"" + name + "\" is defined again here, so the " + (kw.equals("def") ? "test" : "class")
          + " on line " + first.nameToken().line() + " never runs; rename or remove one of them.");
      }
    }
  }

  /**
   * The name is read between the two definitions ({@code test_a = mark(test_a)}, {@code class TestB(TestA)}, a
   * {@code @test_a.setter} decorator), so the first one is kept somewhere.
   */
  private static boolean usedBetween(RuleContext ctx, PyStatement first, PyStatement second) {
    Token end = first.last();
    Token start = second.first();
    for (Token t : ctx.file().tokens()) {
      if (t.isIdent(first.name()) && after(t, end) && after(start, t)) {
        return true;
      }
    }
    return false;
  }

  private static boolean after(Token a, Token b) {
    return a.line() > b.line() || a.line() == b.line() && a.column() > b.column();
  }
}
