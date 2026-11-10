package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * try / except around an assertion catches its failure, so the test passes. Reports an {@code except} clause that
 * catches AssertionError (bare, Exception, BaseException, AssertionError) of a try block holding an assertion when the
 * handler only passes, logs, continues or returns. Retry loops (a try inside for / while) and the
 * {@code except AssertionError: ... else: fail} pattern stay silent.
 */
public class ExceptSwallowsAssertion implements TestRule {

  private static final Set<String> KW = Chains.keywords("py");
  private static final Set<String> CATCHES = Set.of("Exception", "BaseException", "AssertionError");
  private static final Set<String> LOG = Set.of("debug", "info", "warning", "warn", "error", "exception", "log");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      if (t.body() instanceof PyStatement def) {
        visit(ctx, def.body(), false);
      }
    }
  }

  private static void visit(RuleContext ctx, List<PyStatement> block, boolean inLoop) {
    for (int i = 0; i < block.size(); i++) {
      PyStatement s = block.get(i);
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      if (kw.equals("try") && !inLoop && hasAssertion(s.body())) {
        List<PyStatement> handlers = new ArrayList<>();
        boolean guardedElse = false;
        for (int j = i + 1; j < block.size(); j++) {
          String k = block.get(j).keyword();
          if (k.equals("except")) {
            handlers.add(block.get(j));
          } else if (k.equals("else")) {
            guardedElse = !swallows(block.get(j).body());
          } else if (!k.equals("finally")) {
            break;
          }
        }
        if (!guardedElse) {
          for (PyStatement h : handlers) {
            String caught = caught(h);
            if (caught != null && swallows(h.body())) {
              ctx.report(h.first(), h.nodes().get(h.nodes().size() - 2).last(), "\"" + caught
                + "\" catches the AssertionError of the assertion in this try block, so the test cannot fail; "
                + "catch a narrower exception or re-raise.");
              break;
            }
          }
        }
      }
      visit(ctx, s.body(), inLoop || kw.equals("for") || kw.equals("while"));
    }
  }

  /** The clause as written ("except Exception", "except:") when it catches AssertionError, else null. */
  private static String caught(PyStatement h) {
    List<Node> n = h.nodes();
    List<Node> type = n.subList(1, n.size() - 1);
    if (type.isEmpty()) {
      return "except:";
    }
    for (int i = 0; i < type.size(); i++) {
      if (Nodes.isIdent(type.get(i), "as")) {
        type = type.subList(0, i);
      }
    }
    List<List<Node>> names = type.size() == 1 && type.get(0) instanceof Node.Group g && g.is("(") ? g.items() : List.of(type);
    for (List<Node> name : names) {
      Token t = name.size() == 1 ? Nodes.leaf(name.get(0)) : null;
      if (t != null && t.kind() == TokenKind.IDENT && CATCHES.contains(t.text())) {
        return "except " + Nodes.text(type).replace(" , ", ", ").replace("( ", "(").replace(" )", ")");
      }
    }
    return null;
  }

  /** Only pass, ..., continue, break, a bare return, a docstring, print or logging calls. */
  private static boolean swallows(List<PyStatement> body) {
    for (PyStatement s : body) {
      List<Node> n = s.nodes();
      Token first = Nodes.leaf(n.get(0));
      if (n.size() == 1 && first != null && (first.isIdent("pass") || first.is("...") || first.isIdent("continue")
        || first.isIdent("break") || first.isIdent("return") || first.kind() == TokenKind.STRING)) {
        continue;
      }
      List<Chain> chains = Chains.direct(s.group(), KW);
      if (s.body().isEmpty() && chains.size() == 1 && chains.get(0).from() == 0 && chains.get(0).to() == n.size()
        && chains.get(0).endsWithCall() && (chains.get(0).path().equals("print") || chains.get(0).names().size() > 1
          && LOG.contains(chains.get(0).lastName()))) {
        continue;
      }
      return false;
    }
    return true;
  }

  private static boolean hasAssertion(List<PyStatement> body) {
    for (PyStatement s : body) {
      String kw = s.keyword();
      if (kw.equals("def") || kw.equals("class")) {
        continue;
      }
      if (AssertionInsideRaises.isAssertion(s) || hasAssertion(s.body())) {
        return true;
      }
    }
    return false;
  }
}
