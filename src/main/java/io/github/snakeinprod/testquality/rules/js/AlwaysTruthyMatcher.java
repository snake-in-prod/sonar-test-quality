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

/**
 * A Jest / Vitest matcher that cannot fail on the value given: a truthiness or definedness matcher on a literal, an
 * array or object literal, a function, a {@code new} object, an unawaited {@code findBy} promise, an array returned by
 * {@code queryAllBy} / {@code querySelectorAll} / {@code filter} / {@code map}; {@code toBeDefined} on a query that
 * returns null; {@code toBe} / {@code toEqual} of an expression with itself. Values the rule cannot classify are left
 * alone.
 */
public class AlwaysTruthyMatcher implements TestRule {

  private static final Set<String> EQUALITY = Set.of("toBe", "toEqual", "toStrictEqual");
  private static final Set<String> ARRAY_FNS = Set.of("querySelectorAll", "getElementsByClassName", "getElementsByTagName",
    "getElementsByName", "filter", "map");
  private static final Set<String> NULL_FNS = Set.of("querySelector", "getElementById", "closest");

  /** What is known of a value: each flag true, false or null (unknown). */
  private record Value(String kind, Boolean truthy, Boolean isNull, Boolean isUndefined) {
  }

  private static final Value TRUTHY = new Value("truthy", true, false, false);

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      for (Chain c : Chains.in(body, keywords)) {
        check(ctx, c, keywords);
      }
    }
  }

  private void check(RuleContext ctx, Chain c, Set<String> keywords) {
    List<Chain.Segment> segs = c.segments();
    int n = segs.size();
    if (n < 4 || !c.firstName().equals("expect") || !(segs.get(1) instanceof Chain.Call value) || value.items().size() != 1
      || !(segs.get(n - 1) instanceof Chain.Call matcherArgs) || !(segs.get(n - 2) instanceof Chain.Name matcherName)) {
      return;
    }
    boolean not = false;
    for (int i = 2; i < n - 2; i++) {
      if (!(segs.get(i) instanceof Chain.Name m) || !m.token().is("not") || not) {
        return; // .resolves, .rejects and anything else: the value is not what the matcher sees
      }
      not = true;
    }
    String matcher = matcherName.token().text();
    String label = "\"" + (not ? ".not." : ".") + matcher + "()\"";
    List<Node> item = value.items().get(0);
    String shown = source(ctx.file(), item.get(0).first(), item.get(item.size() - 1).last());
    if (EQUALITY.contains(matcher) && !not && matcherArgs.items().size() == 1) {
      List<Node> expected = matcherArgs.items().get(0);
      if (Nodes.text(item).equals(Nodes.text(expected)) && (literal(item) != null || plain(item))) {
        ctx.report(c.first(), c.last(), "\"" + shown + "\" is compared with itself, so " + label
          + " cannot fail; compare the value under test with an expected value.");
      }
      return;
    }
    if (!matcherArgs.items().isEmpty()) {
      return;
    }
    Value v = literal(item);
    if (v == null) {
      v = computed(item, keywords);
    }
    if (v == null || !alwaysPasses(matcher, not, v)) {
      return;
    }
    String message = switch (v.kind()) {
      case "literal" -> label + " cannot fail on the literal \"" + shown + "\"; assert on the value under test instead.";
      case "promise" -> "\"" + shown + "\" is a promise, which is always truthy, so " + label + " cannot fail; await it first.";
      case "nullable" -> "\"" + shown + "\" returns null, not undefined, when nothing matches, so " + label
        + " cannot fail; use .not.toBeNull() instead.";
      default -> "\"" + shown + "\" is always truthy, so " + label + " cannot fail; assert on its content instead.";
    };
    ctx.report(c.first(), c.last(), message);
  }

  private static boolean alwaysPasses(String matcher, boolean not, Value v) {
    Boolean result = switch (matcher) {
      case "toBeTruthy" -> v.truthy();
      case "toBeFalsy" -> v.truthy() == null ? null : !v.truthy();
      case "toBeDefined" -> v.isUndefined() == null ? null : !v.isUndefined();
      case "toBeUndefined" -> v.isUndefined();
      case "toBeNull" -> v.isNull();
      default -> null;
    };
    return result != null && result == !not;
  }

  /** {@code true}, {@code false}, {@code null}, {@code undefined}, a number or a plain string, else null. */
  private static Value literal(List<Node> item) {
    if (item.size() != 1 || !(item.get(0) instanceof Node.Leaf l)) {
      return null;
    }
    Token t = l.token();
    String s = t.text();
    if (t.kind() == TokenKind.IDENT) {
      return switch (s) {
        case "true" -> new Value("literal", true, false, false);
        case "false" -> new Value("literal", false, false, false);
        case "null" -> new Value("literal", false, true, false);
        case "undefined" -> new Value("literal", false, false, true);
        default -> null;
      };
    }
    if (t.kind() == TokenKind.NUMBER) {
      if (s.matches("0[xXbBoO].*")) {
        return null;
      }
      return new Value("literal", s.split("[eE]")[0].matches(".*[1-9].*"), false, false);
    }
    if (t.kind() == TokenKind.STRING && (s.startsWith("'") || s.startsWith("\""))) {
      return new Value("literal", s.length() > 2, false, false);
    }
    return null;
  }

  /** An object, array, function, regex, {@code new} object or a known call result, else null. */
  private static Value computed(List<Node> item, Set<String> keywords) {
    Node first = item.get(0);
    if (item.size() == 1) {
      if (first instanceof Node.Group g && (g.is("[") || g.is("{"))) {
        return TRUTHY;
      }
      Token t = Nodes.leaf(first);
      if (t != null && t.kind() == TokenKind.STRING && t.text().startsWith("/")) {
        return TRUTHY;
      }
      if (t != null && t.kind() == TokenKind.STRING && t.text().startsWith("`")
        && t.text().replaceAll("\\$\\{[^}]*}", "").length() > 2) {
        return TRUTHY;
      }
    }
    Node.Group whole = new Node.Group(null, null, List.copyOf(item));
    JsTrees.Fn fn = JsTrees.functionAt(whole, 0);
    if (fn != null && fn.end() == item.size()) {
      return TRUTHY;
    }
    if (Nodes.isIdent(first, "new") && isNewExpression(item)) {
      return TRUTHY;
    }
    List<Chain> chains = Chains.direct(whole, keywords);
    if (chains.size() != 1 || chains.get(0).from() != 0 || chains.get(0).to() != item.size()) {
      return null;
    }
    Chain c = chains.get(0);
    List<Chain.Segment> segs = c.segments();
    if (segs.size() < 2 || !(segs.get(segs.size() - 1) instanceof Chain.Call)
      || !(segs.get(segs.size() - 2) instanceof Chain.Name last)) {
      return null;
    }
    String name = last.token().text();
    boolean method = c.names().size() >= 2;
    if (name.matches("find(All)?By[A-Z]\\w*")) {
      return new Value("promise", true, false, false);
    }
    if (name.matches("queryAllBy[A-Z]\\w*") || method && ARRAY_FNS.contains(name)
      || c.names().size() == 2 && (c.path().matches("Object\\.(keys|values|entries)") || c.path().matches("Array\\.(from|of)"))) {
      return TRUTHY;
    }
    if (name.matches("queryBy[A-Z]\\w*") || method && NULL_FNS.contains(name)) {
      return new Value("nullable", null, null, false);
    }
    return null;
  }

  /** {@code new Name}, {@code new a.B(...)}: nothing after the constructor call. */
  private static boolean isNewExpression(List<Node> item) {
    int i = 1;
    if (i >= item.size() || Nodes.leaf(item.get(i)) == null || Nodes.leaf(item.get(i)).kind() != TokenKind.IDENT) {
      return false;
    }
    i++;
    while (i + 1 < item.size() && Nodes.isLeaf(item.get(i), ".") && Nodes.leaf(item.get(i + 1)) != null
      && Nodes.leaf(item.get(i + 1)).kind() == TokenKind.IDENT) {
      i += 2;
    }
    if (i < item.size() && item.get(i) instanceof Node.Group g && g.is("(")) {
      i++;
    }
    return i == item.size();
  }

  /** Names and member accesses only: no call, literal object, assignment or await, so it reads the same twice. */
  private static boolean plain(List<Node> item) {
    for (Node n : item) {
      Token t = Nodes.leaf(n);
      if (t == null || !(t.kind() == TokenKind.IDENT || t.is(".") || t.is("?."))
        || t.isIdent("await") || t.isIdent("new")) {
        return false;
      }
    }
    return true;
  }

  /** The source text from {@code from} to {@code to} when on one line, shortened; else the first token and "...". */
  private static String source(ParsedFile file, Token from, Token to) {
    if (from.line() != to.line()) {
      return from.text() + "...";
    }
    String s = file.lines().get(from.line() - 1).substring(from.column(), to.endColumn());
    return s.length() > 60 ? s.substring(0, 57) + "..." : s;
  }
}
