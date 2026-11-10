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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * random values feeding an assertion without a fixed seed make the test flaky. Reports a call of the module-level
 * {@code random} functions inside an assertion, or assigned to a name that a later assertion of the test reads. A file
 * that calls {@code random.seed} anywhere stays silent; {@code random.Random(seed)} instances are not module calls.
 */
public class UnseededRandom implements TestRule {

  private static final Set<String> KW = Chains.keywords("py");
  private static final Set<String> FUNCTIONS = Set.of("random", "randint", "randrange", "choice", "choices", "sample", "uniform",
    "gauss", "getrandbits", "triangular", "normalvariate");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null || ctx.file().chains().stream().anyMatch(c -> !c.calls().isEmpty() && m.resolve(c.path()).equals("random.seed"))) {
      return;
    }
    for (TestCase t : ctx.tests().tests()) {
      if (t.body() instanceof PyStatement def) {
        List<PyStatement> statements = new ArrayList<>();
        flatten(def.body(), statements);
        check(ctx, m, statements);
      }
    }
  }

  private static void check(RuleContext ctx, PyModule m, List<PyStatement> statements) {
    Map<String, Chain> assigned = new HashMap<>();
    Set<Chain> reported = new HashSet<>();
    for (PyStatement s : statements) {
      List<Chain> random = Chains.in(s.group(), KW).stream().filter(c -> isRandom(m, c)).toList();
      if (AssertionInsideRaises.isAssertion(s)) {
        List<Chain> sources = new ArrayList<>(random);
        sources.addAll(reads(s.nodes(), assigned));
        sources.stream().filter(reported::add).forEach(c -> report(ctx, c));
        continue;
      }
      List<Node> n = s.nodes();
      Token target = Nodes.leaf(n.get(0));
      if (s.body().isEmpty() && n.size() > 2 && target != null && target.kind() == TokenKind.IDENT && Nodes.isLeaf(n.get(1), "=")) {
        // a name assigned from a random call, or from a name that holds one, carries that call to later assertions
        List<Chain> sources = random.isEmpty() ? reads(n.subList(2, n.size()), assigned) : random;
        if (sources.isEmpty()) {
          assigned.remove(target.text());
        } else {
          assigned.put(target.text(), sources.get(0));
        }
      }
    }
  }

  /** The random calls held by the tracked names these nodes read (attribute names after "." are not reads). */
  private static List<Chain> reads(List<Node> nodes, Map<String, Chain> assigned) {
    List<Chain> out = new ArrayList<>();
    List<Token> tokens = Nodes.tokens(nodes);
    for (int i = 0; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      Chain source = t.kind() == TokenKind.IDENT && (i == 0 || !tokens.get(i - 1).is(".")) ? assigned.get(t.text()) : null;
      if (source != null) {
        out.add(source);
      }
    }
    return out;
  }

  /** {@code random.randint(...)} or {@code randint(...)} imported from random: the module's shared, unseeded generator. */
  private static boolean isRandom(PyModule m, Chain c) {
    List<String> names = c.names();
    return names.size() <= 2 && c.segments().size() == names.size() + 1 && c.endsWithCall() && m.imports().containsKey(names.get(0))
      && FUNCTIONS.contains(c.lastName()) && m.resolve(c.path()).equals("random." + c.lastName())
      && !inLambda(c);
  }

  private static void report(RuleContext ctx, Chain c) {
    ctx.report(c.first(), c.last(), "\"" + code(ctx.file(), c) + "\" feeds an assertion without a fixed seed, so a failure cannot be "
      + "reproduced; call random.seed(...) first or use fixed values.");
  }

  /** The test's statements in source order, nested functions and classes left out. */
  private static void flatten(List<PyStatement> block, List<PyStatement> out) {
    for (PyStatement s : block) {
      if (!s.keyword().equals("def") && !s.keyword().equals("class")) {
        out.add(s);
        flatten(s.body(), out);
      }
    }
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
