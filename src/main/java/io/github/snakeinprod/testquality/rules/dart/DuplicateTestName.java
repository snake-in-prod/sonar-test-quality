package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two tests with the same full name (group path and description) in one top-level function. Descriptions with
 * interpolation or built from expressions, and tests under if / else / loops, are left alone.
 */
public class DuplicateTestName implements TestRule {

  private static final Set<String> GUARDS = Set.of("if", "for", "while");

  @Override
  public void check(RuleContext ctx) {
    Map<String, TestCase> seen = new HashMap<>();
    for (TestCase t : ctx.tests().tests()) {
      if (t.nameToken().kind() != TokenKind.STRING || t.name().contains("$")) {
        continue;
      }
      List<Node.Group> path = new ArrayList<>();
      if (!find(ctx.file().root(), t.start(), path) || path.size() < 2 || conditional(path, t.start())) {
        continue;
      }
      Token scope = path.get(1).first();
      String key = scope.line() + ":" + scope.column() + "\u0000" + t.suite() + "\u0000" + t.name();
      TestCase first = seen.putIfAbsent(key, t);
      if (first != null) {
        ctx.report(t.nameToken(), "\"" + t.name() + "\" repeats the test on line " + first.nameToken().line()
          + " in the same group; give one of them a unique description.");
      }
    }
  }

  /** Fills {@code path} with the groups from the root down to the one holding {@code t}. */
  private static boolean find(Node.Group g, Token t, List<Node.Group> path) {
    path.add(g);
    for (Node n : g.children()) {
      if (n instanceof Node.Leaf l && l.token() == t || n instanceof Node.Group s && find(s, t, path)) {
        return true;
      }
    }
    path.remove(path.size() - 1);
    return false;
  }

  /** Some block on the way, or the test call itself, follows {@code if (...)}, {@code for (...)}, {@code else}. */
  private static boolean conditional(List<Node.Group> path, Token start) {
    for (int i = 0; i < path.size(); i++) {
      List<Node> kids = path.get(i).children();
      int at = -1;
      for (int j = 0; j < kids.size(); j++) {
        Node n = kids.get(j);
        if (i + 1 < path.size() ? n == path.get(i + 1) : Nodes.leaf(n) == start) {
          at = j;
          break;
        }
      }
      if (at > 0 && (Nodes.isIdent(kids.get(at - 1), "else") || Nodes.isIdent(kids.get(at - 1), "do")
        || at > 1 && kids.get(at - 1) instanceof Node.Group cond && cond.is("(") && Nodes.leaf(kids.get(at - 2)) != null
          && GUARDS.contains(Nodes.leaf(kids.get(at - 2)).text()))) {
        return true;
      }
    }
    return false;
  }
}
