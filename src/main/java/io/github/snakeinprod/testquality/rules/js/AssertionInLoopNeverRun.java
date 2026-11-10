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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Assertions only inside a loop over a collection that can be empty pass when it is empty. */
public class AssertionInLoopNeverRun implements TestRule {

  private static final Set<String> ITERATORS = Set.of("forEach", "map");
  /** Roots of collections that are never empty in practice: Array.from({ length: 3 }), range(5). */
  private static final Set<String> FIXED = Set.of("Array", "range", "times", "_");

  /** A loop over {@code collection} whose body spans {@code from}..{@code to}; {@code at} is where to report. */
  private record Loop(Token at, Token from, Token to, String collection) {
    boolean holds(Token t) {
      return compare(from, t) <= 0 && compare(t, to) <= 0;
    }
  }

  @Override
  public void check(RuleContext ctx) {
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null || tc.skipped()) {
        continue;
      }
      List<Token> asserts = assertions(body);
      if (asserts.isEmpty()) {
        continue;
      }
      List<Loop> loops = new ArrayList<>();
      Nodes.groups(body, g -> loops(ctx, body, g, loops));
      if (!asserts.stream().allMatch(a -> loops.stream().anyMatch(l -> l.holds(a)))) {
        continue;
      }
      for (Loop l : loops) {
        boolean outer = loops.stream().noneMatch(o -> o != l && o.holds(l.at()));
        if (outer && asserts.stream().anyMatch(l::holds)) {
          ctx.report(l.at(), "The assertions of this test run only inside this loop over \"" + l.collection()
            + "\", so the test passes when it is empty; also assert its length.");
        }
      }
    }
  }

  /** Where each assertion starts; empty when the test guards with expect.assertions / expect.hasAssertions. */
  private static List<Token> assertions(Node.Group body) {
    List<Token> out = new ArrayList<>();
    for (Chain c : Chains.in(body, Chains.keywords("js"))) {
      if (JsTrees.isAssertion(c)) {
        if (c.names().size() == 2) {
          String n = c.names().get(1);
          if (n.equals("assertions") || n.equals("hasAssertions")) {
            return List.of();
          }
        }
        out.add(c.first());
      }
    }
    Nodes.groups(body, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        if (Nodes.isIdent(kids.get(i), "assert") && !(i > 0 && Nodes.isLeaf(kids.get(i - 1), "."))) {
          out.add(Nodes.leaf(kids.get(i)));
        }
      }
    });
    return out;
  }

  /** The for-of / for-in loops and forEach / map calls directly in g whose collection can be empty. */
  private static void loops(RuleContext ctx, Node.Group body, Node.Group g, List<Loop> out) {
    List<Node> kids = g.children();
    for (int i = 0; i < kids.size(); i++) {
      if (!Nodes.isIdent(kids.get(i), "for")) {
        continue;
      }
      int h = i + 1 < kids.size() && Nodes.isIdent(kids.get(i + 1), "await") ? i + 2 : i + 1;
      if (h >= kids.size() || !(kids.get(h) instanceof Node.Group head) || !head.is("(") || h + 1 >= kids.size()) {
        continue;
      }
      List<Node> hk = head.children();
      int of = -1;
      for (int k = 0; k < hk.size() && of < 0; k++) {
        if (Nodes.isLeaf(hk.get(k), ";")) {
          break;
        }
        if (Nodes.isIdent(hk.get(k), "of") || Nodes.isIdent(hk.get(k), "in")) {
          of = k;
        }
      }
      if (of < 0 || of + 1 >= hk.size()) {
        continue;
      }
      List<Node> collection = hk.subList(Nodes.isIdent(hk.get(of + 1), "await") ? of + 2 : of + 1, hk.size());
      Node first = kids.get(h + 1);
      Token end = first.last();
      if (!(first instanceof Node.Group b && b.is("{"))) {
        int e = h + 1;
        while (e < kids.size() && !Nodes.isLeaf(kids.get(e), ";")) {
          e++;
        }
        end = kids.get(Math.min(e, kids.size() - 1)).last();
      }
      if (!collection.isEmpty() && canBeEmpty(ctx, body, collection)) {
        out.add(new Loop(Nodes.leaf(kids.get(i)), first.first(), end, Nodes.text(collection)));
      }
    }
    for (Chain c : Chains.direct(g, Chains.keywords("js"))) {
      List<Chain.Segment> segs = c.segments();
      for (int s = 1; s + 1 < segs.size(); s++) {
        if (segs.get(s) instanceof Chain.Name n && ITERATORS.contains(n.token().text()) && segs.get(s + 1) instanceof Chain.Call call
          && JsTrees.functionAt(call.args(), 0) != null) {
          Chain receiver = new Chain(segs.subList(0, s), c.container(), c.from(), c.from());
          if (canBeEmpty(ctx, body, receiver)) {
            out.add(new Loop(n.token(), call.args().first(), call.args().last(), UnawaitedAsyncAssertion.describe(receiver)));
          }
          break;
        }
      }
    }
  }

  private static boolean canBeEmpty(RuleContext ctx, Node.Group body, List<Node> collection) {
    if (!(collection.get(0) instanceof Node.Leaf)) {
      return false;
    }
    List<Chain> chains = Chains.direct(new Node.Group(null, null, collection), Chains.keywords("js"));
    return !chains.isEmpty() && chains.get(0).from() == 0 && chains.get(0).to() == collection.size()
      && canBeEmpty(ctx, body, chains.get(0));
  }

  /**
   * A computed collection: a call (not of a fixed-size factory, not getAllBy / findAllBy, which throw when empty), or a
   * variable the test sets from one. Literal tables and names the test does not set stay silent.
   */
  private static boolean canBeEmpty(RuleContext ctx, Node.Group body, Chain c) {
    String root = c.firstName();
    if (FIXED.contains(root) || literal(ctx.file().root(), root) || throwsWhenEmpty(c)) {
      return false;
    }
    List<String> names = c.names();
    if (names.size() == 2 && root.equals("Object") && c.callAfter(1) != null) {
      // Object.keys(x), Object.values(x), Object.entries(x)
      List<List<Node>> items = c.callAfter(1).items();
      return items.size() == 1 && canBeEmpty(ctx, body, items.get(0));
    }
    if (!c.calls().isEmpty()) {
      return !fixedCount(c) && !perArgument(ctx, c);
    }
    List<Node> init = initializer(body, root);
    if (init == null || init.isEmpty()) {
      return false;
    }
    List<Node> expr = Nodes.isIdent(init.get(0), "await") ? init.subList(1, init.size()) : init;
    if (expr.isEmpty() || !(expr.get(0) instanceof Node.Leaf)) {
      return false;
    }
    List<Chain> chains = Chains.direct(new Node.Group(null, null, expr), Chains.keywords("js"));
    return !chains.isEmpty() && chains.get(0).from() == 0 && !chains.get(0).calls().isEmpty()
      && !FIXED.contains(chains.get(0).firstName()) && !literal(ctx.file().root(), chains.get(0).firstName())
      && !throwsWhenEmpty(chains.get(0)) && !fixedCount(chains.get(0)) && !perArgument(ctx, chains.get(0));
  }

  /**
   * A call with arguments of a function the file defines with a rest parameter ({@code function pick(...keys)}): it
   * returns one result per argument, so it is not empty.
   */
  private static boolean perArgument(RuleContext ctx, Chain c) {
    if (c.names().size() != 1 || c.callAfter(0) == null || c.callAfter(0).items().isEmpty()) {
      return false;
    }
    String name = c.firstName();
    boolean[] out = {false};
    Nodes.groups(ctx.file().root(), g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size() && !out[0]; i++) {
        boolean named = Nodes.isIdent(kids.get(i), "function") && Nodes.isIdent(kids.get(i + 1), name)
          || Nodes.isIdent(kids.get(i), name) && Nodes.isLeaf(kids.get(i + 1), "=");
        // the parameter group comes before the body; generic type parameters may hold groups too
        for (int k = i + 2; named && k < kids.size() && k < i + 14 && !(kids.get(k) instanceof Node.Group b && b.is("{")); k++) {
          if (kids.get(k) instanceof Node.Group p && p.is("(") && !p.children().isEmpty() && Nodes.isLeaf(p.children().get(0), "...")) {
            out[0] = true;
          }
        }
      }
    });
    return out[0];
  }

  /** getAllBy* / findAllBy* (Testing Library) throw when nothing matches. */
  private static boolean throwsWhenEmpty(Chain c) {
    return c.names().stream().anyMatch(n -> n.startsWith("getAllBy") || n.startsWith("findAllBy"));
  }

  /** A factory given a count: makeRows(3). */
  private static boolean fixedCount(Chain c) {
    return c.calls().stream().anyMatch(call -> call.items().stream()
      .anyMatch(item -> item.size() == 1 && Nodes.leaf(item.get(0)) != null && Nodes.leaf(item.get(0)).kind() == TokenKind.NUMBER));
  }

  /** The initializer of "const|let|var name = ..." in the test body (nested blocks included), or null. */
  private static List<Node> initializer(Node.Group body, String name) {
    List<List<Node>> found = new ArrayList<>();
    Nodes.groups(body, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token kw = Nodes.leaf(kids.get(i));
        if (kw != null && (kw.isIdent("const") || kw.isIdent("let") || kw.isIdent("var")) && Nodes.isIdent(kids.get(i + 1), name)) {
          found.add(valueAfter(kids, i + 2));
        }
      }
    });
    return found.size() == 1 ? found.get(0) : null;
  }

  /** A "name = [...]" or "name = {...}" anywhere in the file: a literal table. */
  private static boolean literal(Node.Group root, String name) {
    boolean[] out = {false};
    Nodes.groups(root, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i < kids.size() && !out[0]; i++) {
        if (Nodes.isIdent(kids.get(i), name) && !(i > 0 && Nodes.leaf(kids.get(i - 1)) != null
          && Nodes.leaf(kids.get(i - 1)).kind() == TokenKind.OP && Nodes.leaf(kids.get(i - 1)).text().contains("."))) {
          List<Node> v = valueAfter(kids, i + 1);
          out[0] = v != null && !v.isEmpty() && v.get(0) instanceof Node.Group lit && (lit.is("[") || lit.is("{"));
        }
      }
    });
    return out[0];
  }

  /** The nodes after "=" (a type annotation skipped) up to ",", ";" or the end; null when no "=" follows. */
  private static List<Node> valueAfter(List<Node> kids, int i) {
    int j = i;
    if (j < kids.size() && Nodes.isLeaf(kids.get(j), ":")) {
      while (j < kids.size() && !Nodes.isLeaf(kids.get(j), "=") && !Nodes.isLeaf(kids.get(j), ";") && !Nodes.isLeaf(kids.get(j), ",")) {
        j++;
      }
    }
    if (j >= kids.size() || !Nodes.isLeaf(kids.get(j), "=")) {
      return null;
    }
    int e = j + 1;
    while (e < kids.size() && !Nodes.isLeaf(kids.get(e), ";") && !Nodes.isLeaf(kids.get(e), ",")) {
      e++;
    }
    return kids.subList(j + 1, e);
  }

  private static int compare(Token a, Token b) {
    return a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column());
  }
}
