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
import java.util.regex.Pattern;

/**
 * A test whose only assertions are file snapshots ({@code toMatchSnapshot}, {@code toMatchFileSnapshot}). Inline
 * snapshots (the expected value is in the test), error snapshots (they check that something throws) and visual
 * comparisons are not counted as snapshots. Tests that call an assertion helper or a Testing Library query that throws
 * ({@code getBy}, {@code findBy}) check more than the snapshot and stay silent.
 */
public class SnapshotOnlyTest implements TestRule {

  private static final Set<String> SNAPSHOTS = Set.of("toMatchSnapshot", "toMatchFileSnapshot");
  private static final Pattern CHECKING_CALL = Pattern.compile(
    "(assert|expect|verify|validate|check|should|ensure)[A-Z_]\\w*|assert|verify|(get|find)(All)?By[A-Z]\\w*|waitFor\\w*");

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    Set<String> helpers = assertingFunctions(ctx.file().root(), keywords);
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null || tc.skipped() || tc.assertions() == 0) {
        continue;
      }
      Chain first = null;
      int snapshots = 0;
      int counters = 0;
      boolean checks = false;
      for (Chain c : Chains.in(body, keywords)) {
        if (JsTrees.isAssertion(c)) {
          List<String> names = c.names();
          if (names.size() == 2 && (names.get(1).equals("assertions") || names.get(1).equals("hasAssertions"))) {
            counters++;
          } else if (SNAPSHOTS.contains(c.lastName()) && c.endsWithCall()) {
            snapshots++;
            first = first == null ? c : first;
          }
        } else if (callsChecker(c, helpers)) {
          checks = true;
        }
      }
      if (first != null && !checks && snapshots + counters == tc.assertions()) {
        ctx.report(first.first(), first.last(), "This test only checks a snapshot (\"" + first.lastName()
          + "\"), which accepts whatever was recorded first; add an assertion on the values that matter.");
      }
    }
  }

  /** A call of an assertion helper or of a query that throws when nothing matches. */
  private static boolean callsChecker(Chain c, Set<String> helpers) {
    List<Chain.Segment> segs = c.segments();
    for (int i = 0; i + 1 < segs.size(); i++) {
      if (segs.get(i) instanceof Chain.Name n && segs.get(i + 1) instanceof Chain.Call
        && (CHECKING_CALL.matcher(n.token().text()).matches() || helpers.contains(n.token().text()))) {
        return true;
      }
    }
    return false;
  }

  /** Names of the functions declared in the file ({@code function f}, {@code const f = () =>}) that assert. */
  private static Set<String> assertingFunctions(Node.Group root, Set<String> keywords) {
    Set<String> out = new HashSet<>();
    Nodes.groups(root, g -> {
      List<Node> kids = g.children();
      for (int i = 0; i + 1 < kids.size(); i++) {
        Token name = Nodes.leaf(kids.get(i + 1));
        JsTrees.Fn fn = null;
        if (Nodes.isIdent(kids.get(i), "function") && name != null && name.kind() == TokenKind.IDENT) {
          fn = JsTrees.functionAt(g, i);
        } else if (Nodes.isLeaf(kids.get(i + 1), "=") && i + 2 < kids.size() && Nodes.leaf(kids.get(i)) != null
          && Nodes.leaf(kids.get(i)).kind() == TokenKind.IDENT) {
          name = Nodes.leaf(kids.get(i));
          fn = JsTrees.functionAt(g, i + 2);
        }
        if (fn != null && (JsTrees.assertCalls(fn.body()) > 0
          || Chains.in(fn.body(), keywords).stream().anyMatch(JsTrees::isAssertion))) {
          out.add(name.text());
        }
      }
    });
    return out;
  }
}
