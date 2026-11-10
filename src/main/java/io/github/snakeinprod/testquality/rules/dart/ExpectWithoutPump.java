package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An expect on the widget tree right after an action with no pump in between checks the old frame. */
public class ExpectWithoutPump implements TestRule {

  /**
   * Gestures and key events that change state but build no frame. enterText is left out: a text finder reads the
   * controller of an EditableText, which is up to date without a frame.
   */
  private static final Set<String> GESTURES = Set.of("tap", "tapAt", "longPress", "longPressAt", "drag", "dragFrom", "fling",
    "flingFrom", "timedDrag", "timedDragFrom", "sendKeyEvent", "sendKeyDownEvent", "sendKeyUpEvent");

  @Override
  public void check(RuleContext ctx) {
    Map<Node.Group, Token> testers = UnawaitedTesterAction.testers(ctx.file());
    for (TestCase tc : ctx.tests().tests()) {
      Token tester = testers.get(tc.bodyGroup());
      if (tester == null) {
        continue;
      }
      UnawaitedTesterAction.statements(tc.bodyGroup(), stmts -> {
        for (int i = 0; i < stmts.size(); i++) {
          List<Node> s = stmts.get(i);
          String action = UnawaitedTesterAction.testerCall(s, Nodes.isIdent(s.get(0), "await") ? 1 : 0, tester.text(), GESTURES);
          if (action == null) {
            continue;
          }
          // plain expects (on a flag, a mock) may come first; any other statement may pump, so stop there
          for (int j = i + 1; j < stmts.size() && isExpect(stmts.get(j)); j++) {
            if (onWidgets(stmts.get(j))) {
              List<Node> e = stmts.get(j);
              ctx.report(e.get(0).first(), e.get(1).last(), "This expect runs right after \"" + tester.text() + "." + action
                + "(...)\" with no pump, so it checks the frame from before the " + action + "; add \"await " + tester.text()
                + ".pump()\" before it.");
              break;
            }
          }
        }
      });
    }
  }

  private static boolean isExpect(List<Node> s) {
    return s.size() == 2 && Nodes.isIdent(s.get(0), "expect") && s.get(1) instanceof Node.Group g && g.is("(");
  }

  /** The matcher is a finder matcher (findsOneWidget, findsNothing, findsNWidgets(2)...), so the actual is a Finder. */
  private static boolean onWidgets(List<Node> expect) {
    List<List<Node>> args = DartTrees.positionalArgs((Node.Group) expect.get(1));
    if (args.size() < 2) {
      return false;
    }
    Token m = Nodes.leaf(args.get(1).get(0));
    return m != null && m.kind() == TokenKind.IDENT && m.text().startsWith("finds");
  }
}
