package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/** A comparison statement whose result is dropped checks nothing. */
public class ComparisonWithoutAssert implements TestRule {

  private static final Set<String> COMPARISONS = Set.of("==", "!=", "<", ">", "<=", ">=");
  /** Statements that are not plain expressions. */
  private static final Set<String> KEYWORDS = Set.of("assert", "return", "yield", "raise", "del", "import", "from", "global",
    "nonlocal", "pass", "break", "continue", "if", "elif", "else", "while", "for", "try", "except", "finally", "with", "def",
    "class", "match", "case", "lambda");
  /** Top-level words or operators that make the statement do something besides comparing (short-circuit, assignment). */
  private static final Set<String> NOT_PLAIN = Set.of("if", "lambda", "and", "or", "=", ":", ":=", "+=", "-=", "*=", "/=",
    "//=", "%=", "**=", "&=", "|=", "^=", ">>=", "<<=", "@=");

  @Override
  public void check(RuleContext ctx) {
    for (TestCase t : ctx.tests().tests()) {
      for (PyStatement b : ((PyStatement) t.body()).body()) {
        for (PyStatement s : b.walk()) {
          List<Node> nodes = s.nodes();
          if (s.body().isEmpty() && !KEYWORDS.contains(s.keyword()) && isComparison(nodes) && !hasTop(nodes, NOT_PLAIN)) {
            Token from = nodes.get(0).first();
            Token to = nodes.get(nodes.size() - 1).last();
            ctx.report(from, to, "The result of \"" + code(ctx.file(), from, to)
              + "\" is dropped, so it checks nothing; put \"assert\" in front of it.");
          }
        }
      }
    }
  }

  /** A comparison operator at the top level of the expression (not inside brackets). */
  static boolean isComparison(List<Node> nodes) {
    for (Node n : nodes) {
      Token t = Nodes.leaf(n);
      if (t != null && (t.kind() == TokenKind.OP && COMPARISONS.contains(t.text()) || t.isIdent("is") || t.isIdent("in"))) {
        return true;
      }
    }
    return false;
  }

  static boolean hasTop(List<Node> nodes, Set<String> texts) {
    for (Node n : nodes) {
      Token t = Nodes.leaf(n);
      if (t != null && (t.kind() == TokenKind.IDENT || t.kind() == TokenKind.OP) && texts.contains(t.text())) {
        return true;
      }
    }
    return false;
  }

  /** The source text from one token to another, shortened for a message. */
  static String code(ParsedFile file, Token from, Token to) {
    String s;
    if (from.line() == to.line()) {
      s = file.lines().get(from.line() - 1).substring(from.column(), to.endColumn());
    } else {
      s = file.lines().get(from.line() - 1).substring(from.column()).strip() + " ...";
    }
    return s.length() > 60 ? s.substring(0, 57) + "..." : s;
  }
}
