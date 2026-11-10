package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
import io.github.snakeinprod.testquality.lang.python.PyStatement;
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
 * time.sleep in a test makes it slow, and flaky when it waits for something. Only direct calls in the test body count
 * (not in nested functions or lambdas, which usually simulate slow work), {@code sleep(0)} is ignored, and a file that
 * patches sleep anywhere ("sleep" in a string) stays silent.
 */
public class SleepInTest implements TestRule {

  private static final Set<String> KW = Chains.keywords("py");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null || ctx.file().tokens().stream().anyMatch(t -> t.kind() == TokenKind.STRING && t.text().contains("sleep"))) {
      return;
    }
    for (TestCase t : ctx.tests().tests()) {
      if (t.body() instanceof PyStatement def) {
        visit(ctx, m, def.body());
      }
    }
  }

  private static void visit(RuleContext ctx, PyModule m, List<PyStatement> block) {
    for (PyStatement s : block) {
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      for (Chain c : Chains.in(s.group(), KW)) {
        List<String> names = c.names();
        Chain.Call call = c.callAfter(names.size() - 1);
        if (call != null && c.segments().size() == names.size() + 1 && m.imports().containsKey(names.get(0))
          && m.resolve(c.path()).equals("time.sleep") && !zero(call) && !inLambda(c)) {
          ctx.report(c.first(), c.last(), "\"" + code(ctx.file(), c) + "\" slows the test and makes it flaky; "
            + "wait for the condition or fake the clock instead.");
        }
      }
      visit(ctx, m, s.body());
    }
  }

  private static boolean zero(Chain.Call call) {
    List<List<Node>> args = call.items();
    Token t = args.size() == 1 && args.get(0).size() == 1 ? Nodes.leaf(args.get(0).get(0)) : null;
    return t != null && t.kind() == TokenKind.NUMBER && t.text().matches("0+(\\.0*)?|\\.0+");
  }

  private static boolean inLambda(Chain c) {
    return c.container().children().subList(0, c.from()).stream().anyMatch(n -> Nodes.isIdent(n, "lambda"));
  }

  private static String code(ParsedFile f, Chain c) {
    Token from = c.first();
    Token to = c.last();
    return from.line() == to.endLine() ? f.lines().get(from.line() - 1).substring(from.column(), to.endColumn()) : c.path() + "(...)";
  }
}
