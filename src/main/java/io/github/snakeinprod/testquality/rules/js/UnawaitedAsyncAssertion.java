package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.lang.js.JsTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/** A .resolves / .rejects or Playwright expect that is neither awaited nor returned is not waited for, so its failure is lost. */
public class UnawaitedAsyncAssertion implements TestRule {

  /** Playwright web-first matchers: they retry and return a promise. */
  private static final Set<String> PLAYWRIGHT = Set.of("toBeAttached", "toBeChecked", "toBeDisabled", "toBeEditable", "toBeEmpty",
    "toBeEnabled", "toBeFocused", "toBeHidden", "toBeInViewport", "toBeVisible", "toContainText", "toContainClass",
    "toHaveAccessibleDescription", "toHaveAccessibleErrorMessage", "toHaveAccessibleName", "toHaveAttribute", "toHaveClass",
    "toHaveCount", "toHaveCSS", "toHaveId", "toHaveJSProperty", "toHaveRole", "toHaveScreenshot", "toHaveText", "toHaveTitle",
    "toHaveURL", "toHaveValue", "toHaveValues", "toMatchAriaSnapshot", "toBeOK", "toPass");
  /** chai-as-promised. */
  private static final Set<String> CHAI_ASYNC = Set.of("eventually", "fulfilled", "rejected", "rejectedWith");
  /** The promise is handed on: to done, to another then. */
  private static final Set<String> HANDED_ON = Set.of("then", "catch", "finally", "notify");
  private static final Set<String> NOT_STATEMENT = Set.of("await", "return", "yield", "new", "typeof", "void", "throw", "delete",
    "in", "of", "instanceof", "case");

  @Override
  public void check(RuleContext ctx) {
    boolean playwright = DoneAndPromise.playwright(ctx.file());
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      for (Chain c : Chains.in(body, Chains.keywords("js"))) {
        if (JsTrees.isAssertion(c) && isAsync(c, playwright) && !JsTrees.isAwaited(c) && !JsTrees.isReturned(c) && statement(c)) {
          ctx.report(c.first(), c.last(), "\"" + describe(c) + "\" is neither awaited nor returned, so its failure is lost; await or return it.");
        }
      }
    }
  }

  private static boolean isAsync(Chain c, boolean playwright) {
    List<String> names = c.names();
    if (names.stream().anyMatch(HANDED_ON::contains)) {
      return false;
    }
    if (names.size() >= 2 && names.get(1).equals("poll") || names.contains("resolves") || names.contains("rejects")) {
      return true;
    }
    if (names.contains("to") && names.stream().anyMatch(CHAI_ASYNC::contains)) {
      return true;
    }
    return playwright && c.endsWithCall() && PLAYWRIGHT.contains(c.lastName());
  }

  /** The chain is a whole expression statement in a block. */
  static boolean statement(Chain c) {
    if (!c.container().is("{")) {
      return false;
    }
    Node after = c.after();
    if (after != null && !Nodes.isLeaf(after, ";") && (after.first().line() <= c.last().line() || Nodes.leaf(after) != null
      && Nodes.leaf(after).kind() == TokenKind.OP)) {
      return false;
    }
    Node b = c.before();
    if (b == null || Nodes.isLeaf(b, ";") || b instanceof Node.Group g && g.is("{") || Nodes.isIdent(b, "else")
      || Nodes.isIdent(b, "do")) {
      return true;
    }
    if (b instanceof Node.Group g && g.is("(") && c.from() >= 2) {
      Token kw = Nodes.leaf(c.container().children().get(c.from() - 2));
      if (kw != null && (kw.isIdent("if") || kw.isIdent("for") || kw.isIdent("while"))) {
        return true;
      }
    }
    if (b.last().line() >= c.first().line()) {
      return false;
    }
    Token t = Nodes.leaf(b);
    // a new line after a complete expression: automatic semicolon insertion
    if (t == null) {
      return true;
    }
    if (t.kind() == TokenKind.OP) {
      return t.is("++") || t.is("--");
    }
    return !(t.kind() == TokenKind.IDENT && NOT_STATEMENT.contains(t.text()));
  }

  /** "expect(...).resolves.toEqual(...)". */
  static String describe(Chain c) {
    StringBuilder sb = new StringBuilder();
    for (Chain.Segment s : c.segments()) {
      switch (s) {
        case Chain.Name n -> sb.append(n.accessor()).append(n.token().text());
        case Chain.Call call -> sb.append(call.args().children().isEmpty() ? "()" : "(...)");
        case Chain.Index x -> sb.append("[...]");
      }
    }
    return sb.toString();
  }
}
