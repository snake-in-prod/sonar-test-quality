package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A jest.fn() or vi.fn() created and passed in but never asserted on. Only a plain {@code const x = jest.fn()} declared
 * in the test body counts (no implementation, no chained configuration: those are stubs), and only when every other use
 * of {@code x} in the test passes it whole: a call argument, a JSX attribute value, or a property value of an object
 * literal passed as an argument. Any other use (a call, a member access, an assertion, a helper named expect* / assert*
 * / verify* / check*) keeps the rule silent.
 */
public class MockNeverAsserted implements TestRule {

  private static final Pattern ASSERTING = Pattern.compile("(expect|assert|verify|check|should).*", Pattern.CASE_INSENSITIVE);

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      List<Chain> chains = Chains.in(body, keywords);
      Map<Node.Group, Node.Group> parents = new IdentityHashMap<>();
      Nodes.groups(body, g -> g.children().forEach(c -> {
        if (c instanceof Node.Group sub) {
          parents.put(sub, g);
        }
      }));
      for (Chain c : chains) {
        Token name = declared(c);
        if (name != null && passedOnly(name, body, chains, parents)) {
          ctx.report(name, "\"" + name.text() + "\" is passed in but never asserted on; check its calls with expect("
            + name.text() + ").toHaveBeenCalled...() or pass an inline " + c.firstName() + ".fn().");
        }
      }
    }
  }

  /** The variable of {@code const|let x = jest.fn()} (a type annotation allowed), or null. */
  private static Token declared(Chain c) {
    List<String> names = c.names();
    if (names.size() != 2 || !(names.get(0).equals("jest") || names.get(0).equals("vi")) || !names.get(1).equals("fn")
      || c.segments().size() != 3 || !c.callAfter(1).items().isEmpty() || !c.precededBy("=")) {
      return null;
    }
    Node after = c.after();
    if (after != null && !Nodes.isLeaf(after, ";") && after.first().line() == c.last().line()) {
      return null;
    }
    List<Node> kids = c.container().children();
    for (int i = c.from() - 2; i >= 1; i--) {
      if (Nodes.isIdent(kids.get(i - 1), "const") || Nodes.isIdent(kids.get(i - 1), "let")) {
        Token t = Nodes.leaf(kids.get(i));
        boolean typed = i == c.from() - 2 || Nodes.isLeaf(kids.get(i + 1), ":");
        return t != null && t.kind() == TokenKind.IDENT && typed ? t : null;
      }
      if (Nodes.isLeaf(kids.get(i), ";") || Nodes.isLeaf(kids.get(i), "=")) {
        return null;
      }
    }
    return null;
  }

  /** At least one use passes the mock whole, and every other use does too. */
  private static boolean passedOnly(Token name, Node.Group body, List<Chain> chains, Map<Node.Group, Node.Group> parents) {
    int[] passed = {0};
    boolean[] other = {false};
    Nodes.groups(body, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i < kids.size(); i++) {
        Token t = Nodes.leaf(kids.get(i));
        if (t == null || !t.isIdent(name.text()) || t.equals(name)) {
          continue;
        }
        Node prev = i > 0 ? kids.get(i - 1) : null;
        Node next = i + 1 < kids.size() ? kids.get(i + 1) : null;
        Token p = Nodes.leaf(prev);
        if (p != null && (p.is(".") || p.is("?."))) {
          continue; // a member of something else
        }
        if (Nodes.isLeaf(next, "=") && i + 2 < kids.size() && (kids.get(i + 2) instanceof Node.Group v && v.is("{")
          || Nodes.leaf(kids.get(i + 2)) != null && Nodes.leaf(kids.get(i + 2)).kind() == TokenKind.STRING)) {
          continue; // a JSX attribute name
        }
        if (g.is("{") && Nodes.isLeaf(next, ":") && (prev == null || Nodes.isLeaf(prev, ","))) {
          continue; // an object key
        }
        if (passes(g, i, chains, parents)) {
          passed[0]++;
        } else {
          other[0] = true;
        }
      }
    });
    return passed[0] > 0 && !other[0];
  }

  /** The identifier at {@code kids[i]} of {@code g} is passed whole to code that is not an assertion. */
  private static boolean passes(Node.Group g, int i, List<Chain> chains, Map<Node.Group, Node.Group> parents) {
    List<Node> kids = g.children();
    boolean whole = (i == 0 || Nodes.isLeaf(kids.get(i - 1), ",")) && (i + 1 == kids.size() || Nodes.isLeaf(kids.get(i + 1), ","));
    if (g.is("(") && whole) {
      return callArgs(g, chains);
    }
    if (g.is("{") && kids.size() == 1) {
      // JSX attribute value: name={mock}
      Node.Group parent = parents.get(g);
      int at = parent == null ? -1 : parent.children().indexOf(g);
      return at >= 2 && Nodes.isLeaf(parent.children().get(at - 1), "=") && Nodes.leaf(parent.children().get(at - 2)) != null
        && Nodes.leaf(parent.children().get(at - 2)).kind() == TokenKind.IDENT && parent.children().get(at - 2).first().line()
        == g.first().line() && jsxAttribute(parent, at);
    }
    boolean value = i >= 2 && Nodes.isLeaf(kids.get(i - 1), ":") && (i - 2 == 0 || Nodes.isLeaf(kids.get(i - 3), ","))
      && (i + 1 == kids.size() || Nodes.isLeaf(kids.get(i + 1), ","));
    return g.is("{") && (whole || value) && passedObject(g, chains, parents);
  }

  /** An object literal passed whole as an argument, directly or as the property value of such an object. */
  private static boolean passedObject(Node.Group obj, List<Chain> chains, Map<Node.Group, Node.Group> parents) {
    Node.Group parent = parents.get(obj);
    if (parent == null) {
      return false;
    }
    List<Node> kids = parent.children();
    int i = kids.indexOf(obj);
    boolean whole = (i == 0 || Nodes.isLeaf(kids.get(i - 1), ",")) && (i + 1 == kids.size() || Nodes.isLeaf(kids.get(i + 1), ","));
    if (parent.is("(") && whole) {
      return callArgs(parent, chains);
    }
    boolean value = i >= 2 && Nodes.isLeaf(kids.get(i - 1), ":") && (i - 2 == 0 || Nodes.isLeaf(kids.get(i - 3), ","))
      && (i + 1 == kids.size() || Nodes.isLeaf(kids.get(i + 1), ","));
    return parent.is("{") && value && passedObject(parent, chains, parents);
  }

  /** The group is the argument list of a call that is not an assertion or an asserting helper. */
  private static boolean callArgs(Node.Group args, List<Chain> chains) {
    for (Chain c : chains) {
      for (Chain.Call call : c.calls()) {
        if (call.args() == args) {
          return c.names().stream().noneMatch(n -> ASSERTING.matcher(n).matches() || n.startsWith("toHave") || n.startsWith("toBe"));
        }
      }
    }
    return false;
  }

  /** {@code parent.children()[at]} is the value of an attribute inside a JSX tag: a "<" opens the tag before it. */
  private static boolean jsxAttribute(Node.Group parent, int at) {
    for (int k = at - 1; k >= 0; k--) {
      Node n = parent.children().get(k);
      if (Nodes.isLeaf(n, "<")) {
        return true;
      }
      if (Nodes.isLeaf(n, ";") || Nodes.isLeaf(n, ">") || Nodes.isLeaf(n, "/>")) {
        return false;
      }
    }
    return false;
  }
}
