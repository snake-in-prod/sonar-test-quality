package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * expectLater as a statement, not awaited, in a widget test. Only widget tests are checked: package:test keeps a plain
 * test alive until the matcher settles (and the bloc idiom subscribes with expectLater before adding events), while a
 * widget test runs in fake async and tears the tree down when the body returns.
 */
public class UnawaitedExpectLater implements TestRule {

  private static final Set<String> CONTROL = Set.of("if", "for", "while", "do", "try");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null || !body.is("{") || t.start().is("test")) {
        continue;
      }
      statements(body, s -> {
        if (s.size() == 2 && Nodes.isIdent(s.get(0), "expectLater") && s.get(1) instanceof Node.Group args && args.is("(")) {
          ctx.report(s.get(0).first(), args.last(),
            "\"expectLater(...)\" is not awaited, so this widget test can end before it checks; add \"await\" before it.");
        }
      });
    }
  }

  /**
   * Every statement of a block and of the blocks of its if / for / while / do / try statements; closures, switch cases
   * and other nested code are left out because their timing is not the test's.
   */
  static void statements(Node.Group block, Consumer<List<Node>> visitor) {
    for (List<Node> s : DartTrees.statements(block)) {
      visitor.accept(s);
      if (Nodes.leaf(s.get(0)) != null && CONTROL.contains(Nodes.leaf(s.get(0)).text())) {
        for (Node n : s) {
          if (n instanceof Node.Group g && g.is("{")) {
            statements(g, visitor);
          }
        }
      }
    }
  }
}
