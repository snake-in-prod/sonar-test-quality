package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.lang.js.JsTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/** A test that takes done and is async or returns a promise: Jest fails or waits on the wrong one. */
public class DoneAndPromise implements TestRule {

  private static final Set<String> DONE_NAMES = Set.of("done", "cb", "callback", "finish", "next");
  private static final Set<String> PROMISE_METHODS = Set.of("then", "catch", "finally");

  @Override
  public void check(RuleContext ctx) {
    if (playwright(ctx.file())) {
      return;
    }
    for (TestCase tc : ctx.tests().tests()) {
      JsTrees.Fn fn = fn(ctx.file(), tc);
      Token done = fn == null ? null : done(ctx.file(), tc, fn);
      if (done == null) {
        continue;
      }
      if (fn.async()) {
        ctx.report(done, "This async test also takes \"" + done.text() + "\"; drop the callback and let the test await its promise.");
      } else if (returnsPromise(fn)) {
        ctx.report(done, "This test takes \"" + done.text() + "\" and returns a promise; use one of the two, not both.");
      }
    }
  }

  /** The done parameter of a test, or null: one plain parameter, named like a callback or called in the body. */
  static Token done(ParsedFile file, TestCase tc, JsTrees.Fn fn) {
    if (fn.paramCount() != 1 || fn.simpleParams().size() != 1 || each(file, tc)) {
      return null;
    }
    Token p = fn.simpleParams().get(0);
    if (DONE_NAMES.contains(p.text())) {
      return p;
    }
    boolean called = Chains.in(fn.body(), Chains.keywords("js")).stream()
      .anyMatch(c -> c.firstName().equals(p.text()) && (c.callAfter(0) != null || c.names().size() == 2 && c.callAfter(1) != null
        && c.names().get(1).equals("fail")));
    return called ? p : null;
  }

  /** The function literal of a test, or null. */
  static JsTrees.Fn fn(ParsedFile file, TestCase tc) {
    Node.Group body = tc.bodyGroup();
    if (body == null) {
      return null;
    }
    for (JsTrees.Fn f : JsTrees.functions(file.root())) {
      if (f.body().first().equals(body.first()) && f.body().last().equals(body.last())) {
        return f;
      }
    }
    return null;
  }

  private static boolean each(ParsedFile file, TestCase tc) {
    return file.chains().stream().anyMatch(c -> c.first().equals(tc.start()) && c.names().contains("each"));
  }

  static boolean playwright(ParsedFile file) {
    return file.tokens().stream()
      .anyMatch(t -> t.kind() == TokenKind.STRING && t.text().length() > 2 && t.text().substring(1).startsWith("@playwright/test"));
  }

  /** A "return" of a promise chain in the test's own body, or an expression body that is one. */
  private static boolean returnsPromise(JsTrees.Fn fn) {
    if (fn.expression()) {
      List<Node> kids = fn.body().children();
      return !kids.isEmpty() && promiseAt(fn.body(), 0);
    }
    return ownReturn(fn.body());
  }

  private static boolean ownReturn(Node.Group g) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      if (Nodes.isIdent(kids.get(i), "return") && i + 1 < kids.size() && promiseAt(g, i + 1)) {
        return true;
      }
      JsTrees.Fn inner = JsTrees.functionAt(g, i);
      if (inner != null) {
        i = inner.end() - 1;
      } else if (kids.get(i) instanceof Node.Group sub && sub.is("{") && ownReturn(sub)) {
        return true;
      }
    }
    return false;
  }

  /** A promise expression starts at child i: new Promise(...), Promise.x(...), or a chain with a then / catch / finally call. */
  private static boolean promiseAt(Node.Group g, int i) {
    List<Node> kids = g.children();
    if (Nodes.isIdent(kids.get(i), "new")) {
      return i + 1 < kids.size() && Nodes.isIdent(kids.get(i + 1), "Promise");
    }
    for (Chain c : Chains.direct(g, Chains.keywords("js"))) {
      if (c.from() == i) {
        List<String> names = c.names();
        return names.get(0).equals("Promise") && c.endsWithCall()
          || names.get(0).equals("expect") && (names.contains("resolves") || names.contains("rejects"))
          || c.segments().stream().anyMatch(s -> s instanceof Chain.Name n && PROMISE_METHODS.contains(n.token().text()))
          && c.endsWithCall();
      }
    }
    return false;
  }
}
