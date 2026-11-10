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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A verify or when whose argument is not a call on a mock checks nothing: a method reference ({@code verify(m.save)},
 * {@code verify(() => m.save)}) or the mock itself ({@code verify(m)}). A member counts as a method only when the file
 * calls it somewhere, so getters ({@code when(m.count)}) stay silent. The direct form needs a mockito import.
 */
public class VerifyWithoutCall implements TestRule {

  private static final Set<String> CALLS = Set.of("verify", "verifyNever", "when");

  @Override
  public void check(RuleContext ctx) {
    Node.Group root = ctx.file().root();
    boolean mockito = DartTrees.imports(root, "mockito");
    if (!mockito && !DartTrees.imports(root, "mocktail")) {
      return;
    }
    Set<String> mocks = mockVariables(root, MockNeverVerified.mockClasses(root));
    Set<String> methods = new HashSet<>();
    for (Chain c : ctx.file().chains()) {
      List<Chain.Segment> s = c.segments();
      for (int i = 1; i + 1 < s.size(); i++) {
        if (s.get(i) instanceof Chain.Name n && !n.accessor().isEmpty() && s.get(i + 1) instanceof Chain.Call) {
          methods.add(n.token().text());
        }
      }
    }
    for (TestCase t : ctx.tests().tests()) {
      if (t.bodyGroup() == null) {
        continue;
      }
      for (Chain c : Chains.in(t.bodyGroup(), Chains.keywords("dart"))) {
        Chain.Call call = CALLS.contains(c.firstName()) ? c.callAfter(0) : null;
        if (call == null || call.items().size() != 1) {
          continue;
        }
        List<Node> item = call.items().get(0);
        DartTrees.Closure closure = DartTrees.closure(item);
        List<Node> expr;
        if (closure != null) {
          if (!closure.arrow() || !closure.params().children().isEmpty()) {
            continue;
          }
          expr = closure.body().children();
        } else if (mockito) {
          expr = item;
        } else {
          continue;
        }
        String code = memberPath(expr);
        if (code == null) {
          continue;
        }
        Token first = expr.get(0).first();
        Token last = expr.get(expr.size() - 1).last();
        String what = c.firstName().equals("when") ? "stubs" : "checks";
        if (expr.size() == 1 && mocks.contains(code)) {
          ctx.report(first, last, "\"" + code + "\" in " + c.firstName() + " is the mock itself, not a call on it, so it " + what
            + " nothing; name the call, as in \"" + code + ".method(...)\".");
        } else if (expr.size() > 1 && Character.isLowerCase(code.charAt(0)) && methods.contains(last.text())) {
          ctx.report(first, last, "\"" + code + "\" in " + c.firstName() + " is a method reference, not a call, so it " + what
            + " nothing; call it: \"" + code + "(...)\".");
        }
      }
    }
  }

  /** {@code a}, {@code a.b}, {@code a?.b.c} as text, or null for anything else. */
  private static String memberPath(List<Node> expr) {
    if (expr.size() % 2 == 0) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < expr.size(); i++) {
      Token t = Nodes.leaf(expr.get(i));
      boolean ok = t != null && (i % 2 == 0 ? t.kind() == TokenKind.IDENT : t.is(".") || t.is("?."));
      if (!ok) {
        return null;
      }
      sb.append(t.text());
    }
    return sb.toString();
  }

  /** Variables declared or assigned anywhere in the file with a mock: {@code late MockX x;}, {@code x = MockX();}. */
  private static Set<String> mockVariables(Node.Group root, Set<String> classes) {
    Set<String> out = new HashSet<>();
    Nodes.groups(root, g -> {
      List<Node> k = g.children();
      for (int i = 0; i + 2 < k.size(); i++) {
        Token a = Nodes.leaf(k.get(i));
        Token b = Nodes.leaf(k.get(i + 1));
        Token c = Nodes.leaf(k.get(i + 2));
        if (a == null || b == null || a.kind() != TokenKind.IDENT) {
          continue;
        }
        if (MockNeverVerified.isMockClass(a.text(), classes) && b.kind() == TokenKind.IDENT && c != null
          && (c.is(";") || c.is("=") || c.is(","))) {
          out.add(b.text());
        } else if (b.is("=") && c != null && MockNeverVerified.isMockClass(c.text(), classes) && i + 3 < k.size()
          && k.get(i + 3) instanceof Node.Group args && args.is("(")) {
          out.add(a.text());
        }
      }
    });
    return out;
  }
}
