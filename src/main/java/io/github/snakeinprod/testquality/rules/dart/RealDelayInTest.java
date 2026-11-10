package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Map;

/**
 * Future.delayed, sleep or a real Timer in a widget test instead of tester.pump(duration) or fake async. Only waits of the
 * test's own sequence count: an awaited Future.delayed or a sleep as a statement of the body. Delays inside closures
 * (stubs, callbacks, tester.runAsync) and Timers, which are fake in a widget test, are left alone.
 */
public class RealDelayInTest implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    Map<Node.Group, Token> testers = UnawaitedTesterAction.testers(ctx.file());
    for (TestCase tc : ctx.tests().tests()) {
      Token tester = testers.get(tc.bodyGroup());
      if (tester == null) {
        continue;
      }
      String pump = "\"await " + tester.text() + ".pump(duration)\"";
      UnawaitedTesterAction.statements(tc.bodyGroup(), stmts -> {
        for (List<Node> s : stmts) {
          if (s.size() == 2 && Nodes.isIdent(s.get(0), "sleep") && s.get(1) instanceof Node.Group g && g.is("(")) {
            ctx.report(s.get(0).first(), g.last(), "\"sleep(...)\" blocks the widget test for real time; use " + pump
              + " to move the fake clock instead.");
            continue;
          }
          for (int i = 0; i + 1 < s.size(); i++) {
            Node.Group call = Nodes.isIdent(s.get(i), "await") ? delayedCall(s, i + 1) : null;
            if (call != null) {
              ctx.report(s.get(i + 1).first(), call.last(), "\"Future.delayed(...)\" waits for real time in a widget test; use "
                + pump + " to move the fake clock instead.");
            }
          }
        }
      });
    }
  }

  /** The call group of {@code Future.delayed(...)} or {@code Future<T>.delayed(...)} starting at {@code i}, or null. */
  private static Node.Group delayedCall(List<Node> s, int i) {
    if (!Nodes.isIdent(s.get(i), "Future")) {
      return null;
    }
    int j = i + 1;
    if (j < s.size() && Nodes.isLeaf(s.get(j), "<")) {
      int depth = 0;
      for (; j < s.size(); j++) {
        Token t = Nodes.leaf(s.get(j));
        depth += t == null ? 0 : t.is("<") ? 1 : t.is(">") ? -1 : t.is(">>") ? -2 : 0;
        if (depth <= 0) {
          break;
        }
      }
      j++;
    }
    return j + 2 < s.size() && Nodes.isLeaf(s.get(j), ".") && Nodes.isIdent(s.get(j + 1), "delayed")
      && s.get(j + 2) instanceof Node.Group g && g.is("(") ? g : null;
  }
}
