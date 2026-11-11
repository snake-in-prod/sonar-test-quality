package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartSupport;
import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A widget test whose only check is matchesGoldenFile. Silent when a local helper gets the tester, since it may hold
 * the other checks.
 */
public class GoldenOnlyTest implements TestRule {

  /** Bare functions that take the tester and never assert. */
  private static final Set<String> NOT_HELPERS = Set.of("expect", "expectLater", "matchesGoldenFile", "unawaited");

  @Override
  public void check(RuleContext ctx) {
    Map<String, DartSupport.TestFunction> fns = DartSupport.testFunctions(ctx.file());
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null || !DartSupport.calls(fns, t, "testWidgets")) {
        continue;
      }
      Chain firstGolden = null;
      int goldens = 0;
      int assertions = 0;
      boolean helper = false;
      for (Chain c : Chains.in(body, Chains.keywords("dart"))) {
        Chain.Call call = c.callAfter(0);
        if (call == null) {
          continue;
        }
        if (DartSupport.ASSERTIONS.contains(c.firstName())) {
          assertions++;
          if (isGolden(c.firstName(), call)) {
            goldens++;
            firstGolden = firstGolden == null ? c : firstGolden;
          }
        } else if (c.names().size() == 1 && !NOT_HELPERS.contains(c.firstName()) && passesTester(call)) {
          helper = true;
        }
      }
      if (goldens > 0 && goldens == assertions && !helper) {
        ctx.report(firstGolden.first(), firstGolden.callAfter(0).args().last(), "This widget test only compares a golden file; "
          + "also expect on what the widget shows so a failure says what broke.");
      }
    }
  }

  /** {@code expectLater(x, matchesGoldenFile(...))}, {@code expect(...)} alike, or a golden_toolkit screen golden. */
  private static boolean isGolden(String name, Chain.Call call) {
    if (name.equals("screenMatchesGolden") || name.equals("multiScreenGolden")) {
      return true;
    }
    List<List<Node>> pos = DartTrees.positionalArgs(call.args());
    return pos.size() >= 2 && Nodes.isIdent(pos.get(1).get(0), "matchesGoldenFile");
  }

  private static boolean passesTester(Chain.Call call) {
    return call.items().stream().anyMatch(item -> item.size() == 1 && Nodes.isIdent(item.get(0), "tester"));
  }
}
