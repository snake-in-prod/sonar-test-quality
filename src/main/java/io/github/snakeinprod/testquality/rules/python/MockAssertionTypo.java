package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A misspelled assert method on a mock, or a non-assert method used as one (called_once_with), is accepted by a plain Mock and silently checks nothing. */
public class MockAssertionTypo implements TestRule {

  /** The assertion methods of Mock and AsyncMock. */
  static final List<String> ASSERTIONS = List.of("assert_called", "assert_called_once", "assert_called_with",
    "assert_called_once_with", "assert_any_call", "assert_has_calls", "assert_not_called", "assert_awaited", "assert_awaited_once",
    "assert_awaited_with", "assert_awaited_once_with", "assert_any_await", "assert_has_awaits", "assert_not_awaited");

  /** Assertion names without their "assert_" prefix: a plain Mock creates them on access and they check nothing. */
  static final Set<String> PSEUDO = Set.of("called_once", "called_with", "called_once_with", "not_called", "has_calls", "any_call",
    "awaited_once", "awaited_with", "awaited_once_with", "not_awaited", "has_awaits", "any_await");

  /** Prefixes Mock refuses or that clearly meant "assert". */
  private static final List<String> PREFIXES = List.of("assert", "assret", "asert", "aseert", "assrt");

  /** Frequent made-up assertions too far from a real one for the spelling check, with what to use. */
  private static final Map<String, String> MADE_UP = Map.of(
    "assert_called_twice", "assert m.call_count == 2",
    "assert_not_called_with", "assert call(...) not in m.call_args_list",
    "assert_not_called_once", "assert_not_called");

  @Override
  public void check(RuleContext ctx) {
    Set<String> local = definedNames(ctx.file());
    for (TestCase t : ctx.tests().tests()) {
      for (PyStatement s : statements(t)) {
        for (Chain c : Chains.in(s.group(), Chains.keywords("py"))) {
          check(ctx, c, local);
        }
      }
    }
  }

  private static void check(RuleContext ctx, Chain c, Set<String> local) {
    List<Chain.Segment> segs = c.segments();
    int names = 0;
    for (int i = 0; i + 1 < segs.size(); i++) {
      if (!(segs.get(i) instanceof Chain.Name n)) {
        continue;
      }
      names++;
      String name = n.token().text();
      if (names == 1 || !(segs.get(i + 1) instanceof Chain.Call) || local.contains(name) || ownHelper(c, names)) {
        continue;
      }
      String message = null;
      String near = nearest(name);
      if (near != null) {
        message = "\"" + name + "\" is not a mock assertion; did you mean \"" + near + "\"?";
      } else if (MADE_UP.containsKey(name)) {
        message = "\"" + name + "\" is not a mock assertion and checks nothing; use " + MADE_UP.get(name) + " instead.";
      } else if (PSEUDO.contains(name)) {
        message = "\"" + name + "\" is not a mock assertion and checks nothing; call \"assert_" + name + "\" instead.";
      }
      if (message != null) {
        ctx.report(n.token(), message);
      }
    }
  }

  /** {@code self.helper(...)}: a method of the test class, not a mock. */
  private static boolean ownHelper(Chain c, int nameIndex) {
    String first = c.firstName();
    return nameIndex == 2 && (first.equals("self") || first.equals("cls"));
  }

  /** The real assertion a misspelled one meant (edit distance 1 or 2), or null when it is real or not close to one. */
  static String nearest(String name) {
    if (ASSERTIONS.contains(name) || PREFIXES.stream().noneMatch(name::startsWith)) {
      return null;
    }
    String best = null;
    int bestDistance = 3;
    for (String a : ASSERTIONS) {
      int d = distance(name, a);
      if (d < bestDistance) {
        best = a;
        bestDistance = d;
      }
    }
    return best;
  }

  private static int distance(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        cur[j] = Math.min(sub, Math.min(prev[j], cur[j - 1]) + 1);
      }
      int[] x = prev;
      prev = cur;
      cur = x;
    }
    return prev[b.length()];
  }

  /** Every statement of the test body, nested ones included. */
  static List<PyStatement> statements(TestCase t) {
    List<PyStatement> out = new ArrayList<>();
    if (t.body() instanceof PyStatement def) {
      def.body().forEach(b -> out.addAll(b.walk()));
    }
    return out;
  }

  /**
   * Names the file defines itself ({@code def name}, {@code x.name = ...}): a fake or helper with that member is not a
   * mock, so its methods and attributes are left alone.
   */
  static Set<String> definedNames(ParsedFile file) {
    Set<String> out = new HashSet<>();
    List<Token> tokens = file.tokens();
    for (int i = 0; i + 1 < tokens.size(); i++) {
      Token t = tokens.get(i);
      Token next = tokens.get(i + 1);
      if (next.kind() != TokenKind.IDENT) {
        continue;
      }
      if (t.isIdent("def") || t.is(".") && i + 2 < tokens.size() && tokens.get(i + 2).is("=")) {
        out.add(next.text());
      }
    }
    return out;
  }
}
