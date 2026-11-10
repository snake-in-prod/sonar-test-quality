package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartTrees;
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

/**
 * A matcher that cannot fail on the value: {@code anything}, {@code isA<dynamic>()}, {@code isNotNull} on a finder or a
 * constructor call, a literal checked against itself ({@code expect(true, isTrue)}), a variable compared with itself.
 * A getter compared with itself ({@code expect(a.future, a.future)}) may build a new object on each read, and a variable
 * compared with itself in a test that also uses {@code isNot} checks the == contract, so both stay silent.
 */
public class TrivialMatcher implements TestRule {

  private static final Set<String> MATCH_ALL = Set.of("anything", "isA<dynamic>()", "isA<Object?>()");
  private static final Set<String> EQUALITY = Set.of("equals", "same");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null) {
        continue;
      }
      boolean equalityContract = Nodes.tokens(body.children()).stream().anyMatch(tok -> tok.isIdent("isNot"));
      for (Chain c : Chains.in(body, Chains.keywords("dart"))) {
        if (c.segments().size() != 2 || !(c.firstName().equals("expect") || c.firstName().equals("expectLater")) || c.callAfter(0) == null) {
          continue;
        }
        List<List<Node>> pos = DartTrees.positionalArgs(c.callAfter(0).args());
        if (pos.size() != 2) {
          continue;
        }
        String message = message(pos.get(0), pos.get(1), equalityContract);
        if (message != null) {
          ctx.report(pos.get(1).get(0).first(), pos.get(1).get(pos.get(1).size() - 1).last(), message);
        }
      }
    }
  }

  private static String message(List<Node> actual, List<Node> matcher, boolean equalityContract) {
    String m = compact(matcher);
    String a = compact(actual);
    if (MATCH_ALL.contains(m)) {
      return "\"" + m + "\" matches every value, so this expect cannot fail; use a matcher that states the expected value.";
    }
    if (m.equals("isNotNull") || m.equals("isA<Finder>()")) {
      if (isFinder(actual)) {
        return "\"" + a + "\" is a Finder, never null, so \"" + m + "\" cannot fail; use findsOneWidget or findsWidgets.";
      }
      if (m.equals("isNotNull") && isConstructorCall(actual)) {
        return "A constructor call is never null, so \"isNotNull\" cannot fail; check a property of the created object.";
      }
    }
    String literal = literal(actual);
    if (literal != null && (m.equals(literal) || m.equals("equals(" + literal + ")") || m.equals(literalMatcher(literal))
      || m.equals("isNotNull") && !literal.equals("null"))) {
      return "\"" + a + "\" is a constant, so \"" + m + "\" cannot fail; check a value produced by the code under test.";
    }
    String inner = equalityOperand(matcher);
    if (!equalityContract && isVariable(actual) && (a.equals(m) || a.equals(inner))) {
      return "\"" + a + "\" is compared with itself, so this expect cannot fail; compare it with the expected value.";
    }
    return null;
  }

  private static String compact(List<Node> nodes) {
    StringBuilder sb = new StringBuilder();
    for (Token t : Nodes.tokens(nodes)) {
      sb.append(t.text());
    }
    return sb.toString();
  }

  /** {@code find.byKey(k)}, {@code find.text('a').first}: the finder API always returns a Finder. */
  private static boolean isFinder(List<Node> actual) {
    Chain c = whole(actual);
    return c != null && c.firstName().equals("find") && c.names().size() >= 2 && c.callAfter(1) != null;
  }

  /** {@code Widget()} or {@code const Widget(...)}: an unnamed constructor, which never returns null. */
  private static boolean isConstructorCall(List<Node> actual) {
    List<Node> rest = Nodes.isIdent(actual.get(0), "const") || Nodes.isIdent(actual.get(0), "new") ? actual.subList(1, actual.size()) : actual;
    Token name = rest.size() == 2 ? Nodes.leaf(rest.get(0)) : null;
    return name != null && name.kind() == TokenKind.IDENT && Character.isUpperCase(name.text().charAt(0))
      && rest.get(1) instanceof Node.Group g && g.is("(");
  }

  /** The chain that is the whole expression, or null. */
  private static Chain whole(List<Node> nodes) {
    List<Chain> chains = Chains.direct(new Node.Group(null, null, nodes), Chains.keywords("dart"));
    return chains.size() == 1 && chains.get(0).from() == 0 && chains.get(0).to() == nodes.size() ? chains.get(0) : null;
  }

  /** {@code true}, {@code null}, {@code 42}, {@code 'text'} without interpolation, or null. */
  private static String literal(List<Node> actual) {
    Token t = actual.size() == 1 ? Nodes.leaf(actual.get(0)) : null;
    if (t == null) {
      return null;
    }
    boolean constant = t.kind() == TokenKind.NUMBER || t.kind() == TokenKind.STRING && !t.text().contains("$")
      || t.isIdent("true") || t.isIdent("false") || t.isIdent("null");
    return constant ? t.text() : null;
  }

  private static String literalMatcher(String literal) {
    return switch (literal) {
      case "true" -> "isTrue";
      case "false" -> "isFalse";
      case "null" -> "isNull";
      default -> "";
    };
  }

  /** The operand of {@code equals(x)} or {@code same(x)}, compacted, or "". */
  private static String equalityOperand(List<Node> matcher) {
    Token name = matcher.size() == 2 ? Nodes.leaf(matcher.get(0)) : null;
    if (name != null && EQUALITY.contains(name.text()) && matcher.get(1) instanceof Node.Group g && g.is("(") && g.items().size() == 1) {
      return compact(g.items().get(0));
    }
    return "";
  }

  /** A bare name such as {@code user}: reading it twice gives the same object. */
  private static boolean isVariable(List<Node> actual) {
    Token t = actual.size() == 1 ? Nodes.leaf(actual.get(0)) : null;
    return t != null && t.kind() == TokenKind.IDENT && !t.text().equals("nan");
  }
}
