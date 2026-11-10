package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartSupport;
import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;

/** {@code skip: true} or {@code skip: ''} on a test or group, and {@code @Skip()} on the library, give no reason. */
public class SkipWithoutReason implements TestRule {

  @Override
  public void check(RuleContext ctx) {
    List<Node> top = ctx.file().root().children();
    for (int i = 0; i + 2 < top.size(); i++) {
      if (Nodes.isLeaf(top.get(i), "@") && Nodes.isIdent(top.get(i + 1), "Skip") && top.get(i + 2) instanceof Node.Group args
        && args.is("(") && args.children().isEmpty()) {
        ctx.report(top.get(i).first(), args.last(), "\"@Skip()\" gives no reason; pass one, as in @Skip('reason'), so reports say"
          + " why the file is skipped.");
      }
    }
    for (Chain c : ctx.file().chains()) {
      String name = c.firstName();
      Chain.Call call = c.callAfter(0);
      if (call == null || c.segments().size() != 2 || !DartSupport.TEST_FUNCTIONS.contains(name) && !name.equals("group")
        || c.after() instanceof Node.Group g && g.is("{") || Nodes.isLeaf(c.after(), "=>")) {
        continue;
      }
      for (List<Node> item : call.items()) {
        if (item.size() == 3 && Nodes.isIdent(item.get(0), "skip") && Nodes.isLeaf(item.get(1), ":") && noReason(Nodes.leaf(item.get(2)))) {
          Token value = Nodes.leaf(item.get(2));
          ctx.report(item.get(0).first(), value, "\"skip: " + value.text() + "\" gives no reason; pass one, as in skip: 'flaky"
            + " on CI, see the issue', so reports say why.");
        }
      }
    }
  }

  private static boolean noReason(Token v) {
    return v != null && (v.isIdent("true") || v.kind() == TokenKind.STRING && DartTrees.stringValue(v).isBlank());
  }
}
