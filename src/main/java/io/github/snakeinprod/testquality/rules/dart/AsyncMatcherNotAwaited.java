package io.github.snakeinprod.testquality.rules.dart;

import io.github.snakeinprod.testquality.lang.dart.DartTrees;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/**
 * completion, throwsA or emits on a future or stream in a test that ends before it settles may never check.
 * Only widget tests are checked: package:test keeps a plain test alive until an async matcher settles, the fake-async
 * widget test does not drive the future and needs {@code await expectLater}.
 */
public class AsyncMatcherNotAwaited implements TestRule {

  private static final Set<String> ASYNC_CALLS = Set.of("completion", "emits", "emitsInOrder", "emitsInAnyOrder", "emitsAnyOf",
    "emitsThrough", "emitsError", "mayEmit", "mayEmitMultiple", "neverEmits");
  private static final Set<String> ASYNC_CONSTANTS = Set.of("completes", "doesNotComplete", "emitsDone");
  private static final Set<String> THROWS_CONSTANTS = Set.of("throws", "throwsArgumentError", "throwsConcurrentModificationError",
    "throwsCyclicInitializationError", "throwsException", "throwsFormatException", "throwsNoSuchMethodError", "throwsNullThrownError",
    "throwsRangeError", "throwsStateError", "throwsUnimplementedError", "throwsUnsupportedError");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null || !body.is("{") || t.start().is("test")) {
        continue;
      }
      UnawaitedExpectLater.statements(body, s -> {
        if (s.size() == 2 && Nodes.isIdent(s.get(0), "expect") && s.get(1) instanceof Node.Group args && args.is("(")) {
          List<List<Node>> pos = DartTrees.positionalArgs(args);
          String matcher = pos.size() == 2 ? asyncMatcher(pos.get(0), pos.get(1)) : null;
          if (matcher != null) {
            ctx.report(s.get(0).first(), args.last(), "\"" + matcher + "\" settles after this widget test may have ended; use "
              + "\"await expectLater(...)\" instead of \"expect(...)\".");
          }
        }
      });
    }
  }

  /** The name of the async matcher, or null when the matcher is sync or may be (throwsA on a closure or a variable). */
  private static String asyncMatcher(List<Node> actual, List<Node> matcher) {
    Token m = Nodes.leaf(matcher.get(0));
    if (m == null) {
      return null;
    }
    boolean call = matcher.size() == 2 && matcher.get(1) instanceof Node.Group g && g.is("(");
    if (matcher.size() == 1 && ASYNC_CONSTANTS.contains(m.text()) || call && ASYNC_CALLS.contains(m.text())) {
      return m.text();
    }
    boolean throwsMatcher = matcher.size() == 1 && THROWS_CONSTANTS.contains(m.text()) || call && m.is("throwsA");
    return throwsMatcher && isCall(actual) ? m.text() : null;
  }

  /** {@code load()}, {@code repo.fetch(1)}, {@code Future.error(e)}: a call, so the value is its result (a future here), not a function. */
  private static boolean isCall(List<Node> actual) {
    List<Chain> chains = Chains.direct(new Node.Group(null, null, actual), Chains.keywords("dart"));
    return chains.size() == 1 && chains.get(0).from() == 0 && chains.get(0).to() == actual.size() && chains.get(0).endsWithCall();
  }
}
