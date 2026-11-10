package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Two tests with the same name in one describe: reports and filters hit only one. Only literal names of tests that are
 * statements of the same block count, so tests in different branches, helpers or table tests stay silent.
 */
public class ShadowedTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    Map<Token, Chain> byStart = new HashMap<>();
    for (Chain c : ctx.file().chains()) {
      byStart.putIfAbsent(c.first(), c);
    }
    Map<Node.Group, Map<String, TestCase>> seen = new IdentityHashMap<>();
    for (TestCase tc : ctx.tests().tests()) {
      Chain c = byStart.get(tc.start());
      if (c == null || tc.skipped() || c.names().contains("each") || !literal(tc) || !statement(c)) {
        continue;
      }
      TestCase first = seen.computeIfAbsent(c.container(), g -> new HashMap<>()).putIfAbsent(tc.name(), tc);
      if (first != null) {
        ctx.report(tc.nameToken(), "Test \"" + tc.name() + "\" is already defined at line " + first.nameToken().line()
          + " in this describe block; give it a unique name.");
      }
    }
  }

  /** The name is one string literal without interpolation. */
  private static boolean literal(TestCase tc) {
    String text = tc.nameToken().text();
    return tc.nameToken().kind() == TokenKind.STRING && text.length() == tc.name().length() + 2 && !text.contains("${");
  }

  /** The test call starts an unconditional statement: not in a ternary, a logical expression, an assignment or an if. */
  private static boolean statement(Chain c) {
    Node before = c.before();
    if (before instanceof Node.Group g) {
      int i = c.from() - 2;
      Node keyword = g.is("(") && i >= 0 ? c.container().children().get(i) : null;
      return !(Nodes.isIdent(keyword, "if") || Nodes.isIdent(keyword, "while") || Nodes.isIdent(keyword, "for"));
    }
    return before == null || Nodes.isLeaf(before, ";");
  }
}
