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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * page.waitForTimeout or a fixed setTimeout delay makes tests slow and flaky. Reports Playwright
 * {@code waitForTimeout}, Cypress {@code cy.wait(<number>)}, {@code await new Promise(r => setTimeout(r, <number>))},
 * {@code await setTimeout(<number>)} (timers/promises) and {@code await sleep(<number>)} of a sleep helper defined in the
 * file. setTimeout forms stay silent under fake timers and for delays below {@link #MIN_DELAY} ms (flush idioms). A wait
 * followed by a check that nothing changed (an assertion repeated from before the wait, or a synchronous count or
 * {@code .not} assertion) stays silent: absence can only be checked after a fixed window.
 */
public class WaitForTimeout implements TestRule {

  private static final double MIN_DELAY = 10;

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    boolean fakeTimersOutside = false;
    for (Chain c : ctx.file().chains()) {
      if (c.lastName().equals("useFakeTimers") && JsTrees.enclosingTest(ctx.tests(), new Node.Leaf(c.first())) == null) {
        fakeTimersOutside = true;
      }
    }
    Set<String> sleepers = sleepHelpers(ctx);
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      List<Chain> chains = Chains.in(body, keywords);
      boolean fake = fakeTimersOutside || chains.stream().anyMatch(c -> c.lastName().equals("useFakeTimers"));
      for (Chain c : chains) {
        check(ctx, c, fake, sleepers, chains);
      }
      if (!fake) {
        Nodes.groups(body, g -> awaitedPromises(ctx, g, keywords, chains));
      }
    }
  }

  private static void check(RuleContext ctx, Chain c, boolean fake, Set<String> sleepers, List<Chain> testChains) {
    List<String> names = c.names();
    String last = names.get(names.size() - 1);
    boolean candidate = last.equals("waitForTimeout") || last.equals("wait") || last.equals("setTimeout") || sleepers.contains(last);
    if (!candidate || window(testChains, c.first(), c.last())) {
      return;
    }
    if (names.size() >= 2 && last.equals("waitForTimeout") && c.endsWithCall()) {
      Token name = nameToken(c, names.size() - 1);
      ctx.report(name, c.last(), "\"" + String.join(".", names) + "\" waits for a fixed time; wait for the element, response "
        + "or state the test needs instead.");
    } else if (names.equals(List.of("cy", "wait")) && c.segments().size() == 3 && delay(c.callAfter(1)) > 0) {
      ctx.report(nameToken(c, 1), c.last(), "\"cy.wait\" with a number waits for a fixed time; wait on an aliased request "
        + "or an assertion instead.");
    } else if (fake || c.segments().size() != 2 || !c.endsWithCall()) {
      // fake timers do not wait in real time; longer chains are something else
    } else if (names.equals(List.of("setTimeout")) && c.precededBy("await") && c.callAfter(0).items().size() == 1
      && delay(c.callAfter(0)) >= MIN_DELAY) {
      ctx.report(c.first(), c.last(), "\"await setTimeout\" waits for a fixed time; wait for the condition the test needs instead.");
    } else if (sleepers.contains(last) && c.precededBy("await") && c.callAfter(0).items().size() == 1
      && delay(c.callAfter(0)) >= MIN_DELAY) {
      ctx.report(c.first(), c.last(), "\"" + last + "\" waits for a fixed time; wait for the condition the test needs instead.");
    }
  }

  private static Token nameToken(Chain c, int index) {
    int seen = -1;
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Name n && ++seen == index) {
        return n.token();
      }
    }
    return c.first();
  }

  /** {@code await new Promise(r => setTimeout(r, <number>))}, a type argument after Promise allowed. */
  private static void awaitedPromises(RuleContext ctx, Node.Group g, Set<String> keywords, List<Chain> testChains) {
    List<Node> kids = g.children();
    for (int i = 0; i + 3 < kids.size(); i++) {
      if (!Nodes.isIdent(kids.get(i), "await") || !Nodes.isIdent(kids.get(i + 1), "new") || !Nodes.isIdent(kids.get(i + 2), "Promise")) {
        continue;
      }
      int j = i + 3;
      while (j < kids.size() && j < i + 8 && kids.get(j) instanceof Node.Leaf) {
        j++;
      }
      if (j >= kids.size() || !(kids.get(j) instanceof Node.Group args) || !args.is("(")
        || window(testChains, Nodes.leaf(kids.get(i)), args.last())) {
        continue;
      }
      for (Chain c : Chains.in(args, keywords)) {
        List<List<Node>> items = c.callAfter(0) == null ? List.of() : c.callAfter(0).items();
        if (c.names().equals(List.of("setTimeout")) && c.segments().size() == 2 && items.size() == 2
          && number(items.get(1)) >= MIN_DELAY) {
          ctx.report(c.first(), c.last(), "Awaiting a promise around \"setTimeout\" waits for a fixed time; wait for the "
            + "condition the test needs instead.");
        }
      }
    }
  }

  private static final Set<String> COUNTS = Set.of("toHaveBeenCalledTimes", "toBeCalledTimes", "toHaveLength");
  private static final Set<String> EQUALS = Set.of("toBe", "toEqual", "toStrictEqual");

  /**
   * The wait from {@code start} to {@code end} is a window for checking that nothing changes: the first assertion after
   * it repeats one made before it, or is a synchronous {@code .not}, call count, length or number check.
   */
  private static boolean window(List<Chain> chains, Token start, Token end) {
    Set<String> before = new HashSet<>();
    for (Chain a : chains) {
      if (!JsTrees.isAssertion(a)) {
        continue;
      }
      String text = Nodes.text(a.container().children().subList(a.from(), a.to()));
      if (compare(a.first(), start) < 0) {
        before.add(text);
      } else if (compare(a.first(), end) > 0) {
        return before.contains(text) || !JsTrees.isAwaited(a) && steady(a);
      }
    }
    return false;
  }

  private static boolean steady(Chain a) {
    List<String> names = a.names();
    String matcher = a.lastName();
    Chain.Call call = a.callAfter(names.size() - 1);
    return names.contains("not") || COUNTS.contains(matcher)
      || EQUALS.contains(matcher) && call != null && call.items().size() == 1 && number(call.items().get(0)) >= 0;
  }

  private static int compare(Token a, Token b) {
    return a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column());
  }

  /** The first argument as a number when it is a single number literal, else -1. */
  private static double delay(Chain.Call call) {
    List<List<Node>> items = call == null ? List.of() : call.items();
    return items.isEmpty() ? -1 : number(items.get(0));
  }

  private static double number(List<Node> item) {
    Token t = item.size() == 1 ? Nodes.leaf(item.get(0)) : null;
    if (t == null || t.kind() != TokenKind.NUMBER) {
      return -1;
    }
    try {
      return Double.parseDouble(t.text().replace("_", ""));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Names of functions defined in the file whose body is a {@code new Promise} around {@code setTimeout}:
   * {@code function sleep(ms) {...}} or {@code const sleep = (ms) => ...}.
   */
  private static Set<String> sleepHelpers(RuleContext ctx) {
    Set<String> out = new HashSet<>();
    Nodes.groups(ctx.file().root(), g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token name = null;
        int fnAt = -1;
        if (Nodes.isIdent(kids.get(i), "function") && Nodes.leaf(kids.get(i + 1)) != null
          && Nodes.leaf(kids.get(i + 1)).kind() == TokenKind.IDENT) {
          name = Nodes.leaf(kids.get(i + 1));
          fnAt = i;
        } else if ((Nodes.isIdent(kids.get(i), "const") || Nodes.isIdent(kids.get(i), "let")) && i + 3 < kids.size()
          && Nodes.leaf(kids.get(i + 1)) != null && Nodes.leaf(kids.get(i + 1)).kind() == TokenKind.IDENT
          && Nodes.isLeaf(kids.get(i + 2), "=")) {
          name = Nodes.leaf(kids.get(i + 1));
          fnAt = i + 3;
        }
        JsTrees.Fn fn = fnAt >= 0 ? JsTrees.functionAt(g, fnAt) : null;
        if (fn != null && sleeps(fn)) {
          out.add(name.text());
        }
      }
    });
    return out;
  }

  private static boolean sleeps(JsTrees.Fn fn) {
    String text = " " + Nodes.text(fn.body()) + " ";
    return text.contains(" new Promise ") && text.contains(" setTimeout ( ") && fn.paramCount() == 1;
  }
}
