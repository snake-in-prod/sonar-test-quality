package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.lang.js.JsTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** An assertion inside .then, setTimeout or an event handler that the test does not wait for may never run. */
public class AssertionInUnawaitedCallback implements TestRule {

  private static final Set<String> PROMISE = Set.of("then", "catch", "finally");
  private static final Set<String> TIMERS = Set.of("setTimeout", "setInterval", "setImmediate", "queueMicrotask",
    "requestAnimationFrame", "process.nextTick", "window.setTimeout", "global.setTimeout", "globalThis.setTimeout");
  private static final Set<String> EVENTS = Set.of("on", "once", "addEventListener", "addListener");
  /** Fake timers run the callbacks synchronously. */
  private static final Set<String> FAKE_TIMERS = Set.of("useFakeTimers", "runAllTimers", "runOnlyPendingTimers",
    "advanceTimersByTime", "advanceTimersToNextTimer", "runAllTimersAsync", "advanceTimersByTimeAsync", "runTimersToTime", "tick",
    "tickAsync", "fakeAsync", "flush", "install");
  /** A callback that settles something the test may wait on. */
  private static final Set<String> SETTLES = Set.of("resolve", "reject", "done", "finish", "callback", "cb", "next");

  @Override
  public void check(RuleContext ctx) {
    if (ctx.file().tokens().stream().anyMatch(t -> t.isIdent("cy") || t.isIdent("Cypress"))) {
      // Cypress queues its commands and waits for their then callbacks
      return;
    }
    boolean fakeTimers = ctx.file().tokens().stream().anyMatch(t -> FAKE_TIMERS.contains(t.text()));
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      JsTrees.Fn fn = DoneAndPromise.fn(ctx.file(), tc);
      // a single plain parameter is a done callback the test may call from the callback
      if (body == null || !body.is("{") || fn == null || fn.paramCount() == 1 && fn.simpleParams().size() == 1 || guarded(body)) {
        continue;
      }
      List<Node.Group> own = new ArrayList<>();
      ownBlocks(body, own);
      List<Token> tokens = Nodes.tokens(body);
      for (Node.Group block : own) {
        for (Chain c : Chains.direct(block, Chains.keywords("js"))) {
          if (!UnawaitedAsyncAssertion.statement(c) || JsTrees.isAwaited(c) || JsTrees.isReturned(c) || waitsLater(tokens, c)) {
            continue;
          }
          check(ctx, c, block == body, fakeTimers);
        }
      }
    }
  }

  private static void check(RuleContext ctx, Chain c, boolean topLevel, boolean fakeTimers) {
    String path = c.path();
    if (TIMERS.contains(path)) {
      Chain.Call call = c.callAfter(c.names().size() - 1);
      if (!fakeTimers && call != null && asserts(call.args())) {
        ctx.report(lastName(c), "The assertion in this \"" + c.lastName() + "\" callback runs after the test has ended; use fake timers or await a promise that the callback settles.");
      }
      return;
    }
    List<Chain.Segment> segs = c.segments();
    for (int i = 0; i + 1 < segs.size(); i++) {
      if (segs.get(i) instanceof Chain.Name n && segs.get(i + 1) instanceof Chain.Call call && asserts(call.args())) {
        String name = n.token().text();
        if (PROMISE.contains(name) && i > 0) {
          ctx.report(n.token(), "The assertion in this \"" + name + "\" callback is not waited for, so its failure is lost; await or return the promise.");
          return;
        }
        if (EVENTS.contains(name) && i > 0 && i + 2 == segs.size() && topLevel && last(c)) {
          ctx.report(n.token(), "The assertion in this \"" + name + "\" handler can only run after the test has ended; await the event or wait for the handler.");
          return;
        }
      }
    }
  }

  private static Token lastName(Chain c) {
    Token out = c.first();
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Name n) {
        out = n.token();
      }
    }
    return out;
  }

  /** A function argument whose body asserts and does not settle anything. */
  private static boolean asserts(Node.Group args) {
    List<Node> kids = args.children();
    for (int i = 0; i < kids.size(); i++) {
      if (i > 0 && !Nodes.isLeaf(kids.get(i - 1), ",")) {
        continue;
      }
      JsTrees.Fn fn = JsTrees.functionAt(args, i);
      if (fn == null) {
        continue;
      }
      boolean settles = Nodes.tokens(fn.body()).stream().anyMatch(t -> SETTLES.contains(t.text()));
      boolean asserts = JsTrees.assertCalls(fn.body()) > 0
        || Chains.in(fn.body(), Chains.keywords("js")).stream().anyMatch(JsTrees::isAssertion);
      if (asserts && !settles) {
        return true;
      }
    }
    return false;
  }

  /** expect.assertions / expect.hasAssertions make the test fail when the callback did not run. */
  private static boolean guarded(Node.Group body) {
    return Chains.in(body, Chains.keywords("js")).stream()
      .anyMatch(c -> c.firstName().equals("expect") && c.names().size() == 2
        && (c.names().get(1).equals("assertions") || c.names().get(1).equals("hasAssertions")));
  }

  /** An await or return after the chain may wait for the callback. */
  private static boolean waitsLater(List<Token> tokens, Chain c) {
    Token end = c.last();
    boolean after = false;
    for (Token t : tokens) {
      if (after && (t.isIdent("await") || t.isIdent("return"))) {
        return true;
      }
      after |= t == end;
    }
    return false;
  }

  /** Nothing but a ";" follows the chain in its block. */
  private static boolean last(Chain c) {
    List<Node> kids = c.container().children();
    return c.to() == kids.size() || c.to() + 1 == kids.size() && Nodes.isLeaf(kids.get(c.to()), ";");
  }

  /** The body and the blocks nested in it (if, for, try...), function literals left out. */
  private static void ownBlocks(Node.Group g, List<Node.Group> out) {
    out.add(g);
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      JsTrees.Fn inner = JsTrees.functionAt(g, i);
      if (inner != null) {
        i = inner.end() - 1;
      } else if (kids.get(i) instanceof Node.Group sub && sub.is("{")) {
        ownBlocks(sub, out);
      }
    }
  }
}
