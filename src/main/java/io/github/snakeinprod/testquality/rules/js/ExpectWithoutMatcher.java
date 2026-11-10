package io.github.snakeinprod.testquality.rules.js;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.List;
import java.util.Set;

/**
 * An expect that is never finished with a matcher checks nothing: {@code expect(x);}, {@code expect(p).resolves;},
 * {@code expect(x).toBeTruthy;}. Only expect statements with one argument (other libraries take the assertion as
 * arguments) whose value is dropped are reported.
 */
public class ExpectWithoutMatcher implements TestRule {

  private static final Set<String> MODIFIERS = Set.of("not", "resolves", "rejects");
  private static final Set<String> NOT_STATEMENT_START = Set.of("return", "yield", "typeof", "new", "in", "of", "instanceof",
    "case", "delete", "throw", "extends");

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      for (Chain c : Chains.in(body, keywords)) {
        check(ctx, c);
      }
    }
  }

  private void check(RuleContext ctx, Chain c) {
    List<Chain.Segment> segs = c.segments();
    int call = 1;
    if (!c.firstName().equals("expect")) {
      return;
    }
    if (segs.size() > 2 && segs.get(1) instanceof Chain.Name n && (n.token().is("soft") || n.token().is("poll"))) {
      call = 2;
    }
    if (call >= segs.size() || !(segs.get(call) instanceof Chain.Call args) || args.items().size() != 1) {
      return;
    }
    Chain.Name lastName = null;
    for (int i = call + 1; i < segs.size(); i++) {
      if (!(segs.get(i) instanceof Chain.Name n) || n.token().is("to")) {
        return; // a matcher call, an index, or a Chai chain
      }
      lastName = n;
    }
    if (!isStatement(c)) {
      return;
    }
    String expect = source(ctx.file(), c.first(), args.args().last());
    if (lastName == null || MODIFIERS.contains(lastName.token().text())) {
      ctx.report(c.first(), c.last(), "\"" + source(ctx.file(), c.first(), c.last())
        + "\" is never finished with a matcher, so it checks nothing; add one such as .toEqual(expected).");
    } else if (lastName.token().text().matches("to[A-Z]\\w*")) {
      ctx.report(lastName.token(), "\"" + lastName.token().text() + "\" is not called, so \"" + expect
        + "\" checks nothing; add the call parentheses.");
    }
  }

  /** The chain is a whole expression statement: nothing before it but a statement start, nothing after it. */
  private static boolean isStatement(Chain c) {
    if (c.container().is("(") || c.container().is("[")) {
      return false;
    }
    Node before = c.before();
    if (before instanceof Node.Leaf l && (l.token().isIdent("await") || l.token().isIdent("void"))) {
      int i = c.from() - 1;
      before = i > 0 ? c.container().children().get(i - 1) : null;
    }
    return startsStatement(before, c.first()) && endsStatement(c.after(), c.last());
  }

  private static boolean startsStatement(Node before, Token first) {
    if (before == null) {
      return true;
    }
    if (before instanceof Node.Group g) {
      return g.is("{") || g.last().line() < first.line();
    }
    Token t = ((Node.Leaf) before).token();
    if (t.is(";") && t.kind() == TokenKind.OP) {
      return true;
    }
    return t.kind() != TokenKind.OP && t.line() < first.line() && !NOT_STATEMENT_START.contains(t.text());
  }

  private static boolean endsStatement(Node after, Token last) {
    if (after == null) {
      return true;
    }
    Token t = after.first();
    if (after instanceof Node.Leaf l && l.token().kind() == TokenKind.OP) {
      return t.is(";");
    }
    return t.line() > last.line();
  }

  /** The source text from {@code from} to {@code to} when on one line, shortened; else "expect(...)". */
  private static String source(ParsedFile file, Token from, Token to) {
    if (from.line() != to.line()) {
      return from.text() + "(...)";
    }
    String s = file.lines().get(from.line() - 1).substring(from.column(), to.endColumn());
    return s.length() > 60 ? s.substring(0, 57) + "..." : s;
  }
}
