package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/**
 * when(...) on a mock created in the test that the test never passes anywhere: only stubs and verifies mention it, so
 * no code can trigger the stub. Mocks from setUp or the group are left alone (they may be injected there).
 */
public class StubNeverUsed implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    Set<String> classes = MockNeverVerified.mockClasses(ctx.file().root());
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null) {
        continue;
      }
      MockNeverVerified.localMocks(body, classes).forEach((name, decl) -> {
        List<MockNeverVerified.Use> uses = MockNeverVerified.uses(body, name, decl);
        if (uses.isEmpty() || !uses.stream().allMatch(MockNeverVerified.Use::mockApi)) {
          return;
        }
        for (Chain c : Chains.in(body, Chains.keywords("dart"))) {
          Chain.Call when = c.firstName().equals("when") ? c.callAfter(0) : null;
          if (when != null && uses.stream().anyMatch(u -> inside(u.token(), when.args()))) {
            ctx.report(c.first(), when.args().last(), "\"" + name + "\" is stubbed but the test never passes it to the code"
              + " under test; inject it or remove the stub.");
          }
        }
      });
    }
  }

  private static boolean inside(Token t, Node.Group g) {
    return after(t, g.first()) && after(g.last(), t);
  }

  private static boolean after(Token a, Token b) {
    return a.line() > b.line() || a.line() == b.line() && a.column() > b.column();
  }
}
