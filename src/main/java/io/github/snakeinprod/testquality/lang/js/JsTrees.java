package io.github.snakeinprod.testquality.lang.js;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Helpers over JS / TS token trees for the extractor and the rules. */
public final class JsTrees {

  private JsTrees() {
  }

  /**
   * A function literal: an arrow function or a function expression, starting at {@code start} in {@code container}
   * and ending before {@code end}. {@code body} is the "{}" group, or for an expression body a group with null brackets
   * holding the nodes after "=>" ({@code expression} true). {@code params} is the "()" group, or null for a single
   * parameter without parentheses ({@code param}).
   */
  public record Fn(Node.Group container, int start, int end, boolean async, Node.Group params, Token param, Node.Group body,
    boolean expression) {

    /** Parameter names that are plain identifiers (typed or not), in order; destructured parameters are left out. */
    public List<Token> simpleParams() {
      if (param != null) {
        return List.of(param);
      }
      List<Token> out = new ArrayList<>();
      for (List<Node> item : params.items()) {
        Token t = Nodes.leaf(item.get(0));
        if (t != null && t.kind() == TokenKind.IDENT && (item.size() == 1 || Nodes.isLeaf(item.get(1), ":"))) {
          out.add(t);
        }
      }
      return out;
    }

    public int paramCount() {
      return param != null ? 1 : params.items().size();
    }
  }

  /** Every function literal in the tree, nested ones included. */
  public static List<Fn> functions(Node.Group root) {
    List<Fn> out = new ArrayList<>();
    Nodes.groups(root, g -> {
      for (int i = 0; i < g.children().size(); i++) {
        Fn f = functionAt(g, i);
        if (f != null) {
          out.add(f);
          if (f.async()) {
            i++;
          }
        }
      }
    });
    return out;
  }

  /** The function literal starting at child {@code i} of {@code g}, or null. */
  public static Fn functionAt(Node.Group g, int i) {
    List<Node> kids = g.children();
    int s = i;
    boolean async = false;
    if (Nodes.isIdent(kids.get(i), "async") && i + 1 < kids.size() && !Nodes.isLeaf(kids.get(i + 1), "=>")) {
      async = true;
      i++;
    }
    Node first = kids.get(i);
    if (Nodes.isIdent(first, "function")) {
      int j = i + 1;
      if (j < kids.size() && Nodes.isLeaf(kids.get(j), "*")) {
        j++;
      }
      if (j < kids.size() && Nodes.leaf(kids.get(j)) != null && Nodes.leaf(kids.get(j)).kind() == TokenKind.IDENT) {
        j++;
      }
      if (j >= kids.size() || !(kids.get(j) instanceof Node.Group params) || !params.is("(")) {
        return null;
      }
      j++;
      // TypeScript return type
      while (j < kids.size() && kids.get(j) instanceof Node.Leaf l && !l.token().is(",") && !l.token().is(";")) {
        j++;
      }
      if (j < kids.size() && kids.get(j) instanceof Node.Group body && body.is("{")) {
        return new Fn(g, s, j + 1, async, params, null, body, false);
      }
      return null;
    }
    int arrow;
    Node.Group params = null;
    Token param = null;
    if (first instanceof Node.Group p && p.is("(")) {
      arrow = i + 1;
      if (arrow < kids.size() && Nodes.isLeaf(kids.get(arrow), ":")) {
        // TypeScript return type: (x): Promise<void> => ...
        while (arrow < kids.size() && kids.get(arrow) instanceof Node.Leaf l && !l.token().is("=>") && !l.token().is(",")
          && !l.token().is(";")) {
          arrow++;
        }
      }
      params = p;
    } else if (first instanceof Node.Leaf l && l.token().kind() == TokenKind.IDENT && !Chains.KEYWORDS.contains(l.token().text())) {
      arrow = i + 1;
      param = l.token();
    } else {
      return null;
    }
    if (arrow >= kids.size() || !Nodes.isLeaf(kids.get(arrow), "=>")) {
      return null;
    }
    int b = arrow + 1;
    if (b < kids.size() && kids.get(b) instanceof Node.Group body && body.is("{")) {
      return new Fn(g, s, b + 1, async, params, param, body, false);
    }
    // ponytail: an expression body ends at "," or ";" only, so a body relying on ASI runs to the end of its block
    int e = b;
    while (e < kids.size() && !(kids.get(e) instanceof Node.Leaf l && l.token().kind() == TokenKind.OP
      && (l.token().is(",") || l.token().is(";")))) {
      e++;
    }
    if (e == b) {
      return null;
    }
    return new Fn(g, s, e, async, params, param, new Node.Group(null, null, List.copyOf(kids.subList(b, e))), true);
  }

  /** The chain is preceded by "await". */
  public static boolean isAwaited(Chain c) {
    return c.precededBy("await");
  }

  /**
   * The chain is preceded by "return", or it is the whole expression body of an arrow function (in the file tree, or
   * as the whole of an {@link Fn#body()} expression group).
   */
  public static boolean isReturned(Chain c) {
    if (c.precededBy("return")) {
      return true;
    }
    Node after = c.after();
    boolean ends = after == null || Nodes.isLeaf(after, ",") || Nodes.isLeaf(after, ";");
    if (c.precededBy("=>") && ends) {
      return true;
    }
    Node.Group g = c.container();
    return g.open() == null && c.from() == 0 && c.to() == g.children().size();
  }

  /** The test whose source range holds the node, or null. */
  public static TestCase enclosingTest(TestFile tests, Node node) {
    Token t = node.first();
    if (t == null) {
      return null;
    }
    for (TestCase tc : tests.tests()) {
      if (compare(tc.start(), t) <= 0 && compare(t, tc.end()) <= 0) {
        return tc;
      }
    }
    return null;
  }

  private static int compare(Token a, Token b) {
    return a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column());
  }

  private static final Set<String> MOCK_FACTORIES = Set.of("fn", "spyOn", "mock");

  /**
   * An assertion: {@code expect(...)} finished with a matcher call ({@code .not}, {@code .resolves}, {@code expect.soft}
   * included) or a Chai {@code .to} chain, {@code expect.assertions} / {@code expect.hasAssertions}. {@code assert} is a
   * chain keyword (Python), so {@code assert(...)} calls are counted by {@link #assertCalls}.
   */
  public static boolean isAssertion(Chain c) {
    List<String> names = c.names();
    if (!c.firstName().equals("expect")) {
      return false;
    }
    if (names.size() == 2 && (names.get(1).equals("assertions") || names.get(1).equals("hasAssertions"))) {
      return c.callAfter(1) != null;
    }
    List<Chain.Segment> segs = c.segments();
    int call = 0;
    while (call < segs.size() && !(segs.get(call) instanceof Chain.Call)) {
      call++;
    }
    for (int j = call + 1; j < segs.size(); j++) {
      if (segs.get(j) instanceof Chain.Name name) {
        if (j + 1 < segs.size() && segs.get(j + 1) instanceof Chain.Call) {
          return true;
        }
        if (name.token().is("to") && j + 1 < segs.size()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Number of {@code assert(...)} and {@code assert.<name>(...)} calls in the tree. */
  public static int assertCalls(Node.Group root) {
    int[] count = {0};
    Nodes.groups(root, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i < kids.size(); i++) {
        if (!Nodes.isIdent(kids.get(i), "assert") || i > 0 && Nodes.isLeaf(kids.get(i - 1), ".")) {
          continue;
        }
        int j = i + 1;
        while (j + 1 < kids.size() && Nodes.isLeaf(kids.get(j), ".") && Nodes.leaf(kids.get(j + 1)) != null
          && Nodes.leaf(kids.get(j + 1)).kind() == TokenKind.IDENT) {
          j += 2;
        }
        if (j < kids.size() && kids.get(j) instanceof Node.Group call && call.is("(")) {
          count[0]++;
        }
      }
    });
    return count[0];
  }

  /** A mock:{@code jest.fn}, {@code vi.fn}, {@code jest.spyOn}, {@code vi.spyOn}, {@code jest.mock}, {@code vi.mock} calls. */
  public static boolean isMock(Chain c) {
    List<String> names = c.names();
    return names.size() >= 2 && (names.get(0).equals("jest") || names.get(0).equals("vi")) && MOCK_FACTORIES.contains(names.get(1))
      && c.callAfter(1) != null;
  }
}
