package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** A tap, enterText, pump or pumpWidget that is not awaited lets the test check the screen before it changed. */
public class UnawaitedTesterAction implements TestRule {

  /** WidgetTester methods that return a future the test must wait for. */
  private static final Set<String> ACTIONS = Set.of("pumpWidget", "pump", "pumpAndSettle", "pumpFrames", "pumpWidgetBuilder",
    "pumpDeviceBuilder", "tap", "tapAt", "tapOnText", "longPress", "longPressAt", "drag", "dragFrom", "dragUntilVisible",
    "fling", "flingFrom", "timedDrag", "timedDragFrom", "enterText", "showKeyboard", "sendKeyEvent", "sendKeyDownEvent",
    "sendKeyUpEvent", "sendKeyRepeatEvent", "scrollUntilVisible", "ensureVisible", "pageBack", "idle");

  private static final Set<String> BLOCK_KEYWORDS = Set.of("else", "try", "finally", "do");
  private static final Set<String> HEADER_KEYWORDS = Set.of("if", "for", "while", "catch");

  @Override
  public void check(RuleContext ctx) {
    Map<Node.Group, Token> testers = testers(ctx.file());
    for (TestCase tc : ctx.tests().tests()) {
      Token tester = testers.get(tc.bodyGroup());
      if (tester == null) {
        continue;
      }
      statements(tc.bodyGroup(), stmts -> {
        for (List<Node> s : stmts) {
          String action = testerCall(s, 0, tester.text(), ACTIONS);
          if (action != null) {
            ctx.report(s.get(0).first(), s.get(s.size() - 1).last(), "\"" + tester.text() + "." + action
              + "(...)\" is not awaited; add \"await\" so the test goes on only after the action is done.");
          }
        }
      });
    }
  }

  /**
   * The tester parameter of every testWidgets / testGoldens closure with a block body, keyed by that body. Arrow bodies
   * are left out: their expression is returned.
   */
  static Map<Node.Group, Token> testers(ParsedFile file) {
    Map<Node.Group, Token> out = new IdentityHashMap<>();
    Nodes.groups(file.root(), g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        if ((Nodes.isIdent(kids.get(i), "testWidgets") || Nodes.isIdent(kids.get(i), "testGoldens"))
          && kids.get(i + 1) instanceof Node.Group args && args.is("(")) {
          List<List<Node>> pos = DartTrees.positionalArgs(args);
          DartTrees.Closure c = pos.size() > 1 ? DartTrees.closure(pos.get(1)) : null;
          if (c == null || c.arrow() || c.params().items().isEmpty()) {
            continue;
          }
          List<Node> param = c.params().items().get(0);
          Token name = Nodes.leaf(param.get(param.size() - 1));
          if (name != null && name.kind() == TokenKind.IDENT) {
            out.put(c.body(), name);
          }
        }
      }
    });
    return out;
  }

  /**
   * Visits the statements of a block and of the blocks of its if / for / while / try statements, block by block. Closures
   * defined in the block are not entered: what runs there is not the test's own sequence.
   */
  static void statements(Node.Group block, Consumer<List<List<Node>>> visitor) {
    List<List<Node>> stmts = DartTrees.statements(block);
    visitor.accept(stmts);
    for (List<Node> s : stmts) {
      for (int k = 1; k < s.size(); k++) {
        if (s.get(k) instanceof Node.Group g && g.is("{") && isControlBlock(s, k)) {
          statements(g, visitor);
        }
      }
    }
  }

  private static boolean isControlBlock(List<Node> s, int k) {
    Token prev = Nodes.leaf(s.get(k - 1));
    if (prev != null) {
      return prev.kind() == TokenKind.IDENT && BLOCK_KEYWORDS.contains(prev.text());
    }
    Token header = k >= 2 ? Nodes.leaf(s.get(k - 2)) : null;
    return s.get(k - 1) instanceof Node.Group p && p.is("(") && header != null && header.kind() == TokenKind.IDENT
      && HEADER_KEYWORDS.contains(header.text());
  }

  /**
   * The method name when the statement from {@code at} is exactly {@code tester.<method>(...)} with a method of the set,
   * else null.
   */
  static String testerCall(List<Node> s, int at, String tester, Set<String> methods) {
    if (s.size() - at != 4 || !Nodes.isIdent(s.get(at), tester) || !Nodes.isLeaf(s.get(at + 1), ".")
      || !(s.get(at + 3) instanceof Node.Group args) || !args.is("(")) {
      return null;
    }
    Token m = Nodes.leaf(s.get(at + 2));
    return m != null && m.kind() == TokenKind.IDENT && methods.contains(m.text()) ? m.text() : null;
  }
}
