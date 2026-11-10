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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code expect.assertions(n)} with a literal count that differs from the expect assertions the test body makes. Only
 * reported when the static count is exact for the direction of the mismatch: more expects than n needs a body without
 * branches, loops or expects inside callbacks (each expect then runs once); fewer expects than n needs a body without
 * loops, assertion helpers or expects inside callbacks that may run several times ({@code .then} / {@code .catch} /
 * {@code .finally} run at most once).
 */
public class ExpectAssertionsMismatch implements TestRule {

  private static final Set<String> ONCE_CALLBACKS = Set.of("then", "catch", "finally");
  private static final Set<String> LOOPS = Set.of("for", "while", "do");
  private static final Set<String> BRANCHES = Set.of("if", "else", "switch", "case", "try", "catch", "?", "&&", "||", "??");
  private static final Pattern HELPER = Pattern.compile("(assert|expect|verify|validate|check|should|ensure)[A-Z_]\\w*");
  private static final Pattern TEST_MODULE = Pattern.compile("(?i).*(test|spec|helper|util|fixture|support|assert|expect|mock).*");
  private static final Pattern TEST_LIBRARY = Pattern.compile(".(@testing-library/|@jest/|@vitest/|@playwright/|vitest|jest).*");

  @Override
  public void check(RuleContext ctx) {
    Set<String> keywords = Chains.keywords(ctx.file().language());
    Set<String> helpers = null;
    for (TestCase tc : ctx.tests().tests()) {
      Node.Group body = tc.bodyGroup();
      if (body == null) {
        continue;
      }
      List<Chain> chains = Chains.in(body, keywords);
      List<Chain> counters = chains.stream().filter(c -> c.path().equals("expect.assertions") && c.callAfter(1) != null).toList();
      if (counters.size() != 1 || chains.stream().anyMatch(c -> c.path().equals("expect.hasAssertions"))) {
        continue;
      }
      Chain counter = counters.get(0);
      List<List<Node>> arg = counter.callAfter(1).items();
      Token n = arg.size() == 1 && arg.get(0).size() == 1 ? Nodes.leaf(arg.get(0).get(0)) : null;
      if (n == null || n.kind() != TokenKind.NUMBER || !n.text().matches("\\d+")) {
        continue;
      }
      int expected = Integer.parseInt(n.text());
      if (helpers == null) {
        helpers = helpers(ctx.file().root(), keywords);
      }
      check(ctx, body, chains, counter, expected, helpers);
    }
  }

  private void check(RuleContext ctx, Node.Group body, List<Chain> chains, Chain counter, int expected, Set<String> helpers) {
    List<Chain> asserts = new ArrayList<>();
    for (Chain c : chains) {
      if (c != counter && c.firstName().equals("expect") && JsTrees.isAssertion(c)) {
        asserts.add(c);
      }
    }
    int actual = asserts.size();
    if (actual == expected) {
      return;
    }
    Map<Node.Group, String> callNames = new IdentityHashMap<>();
    boolean helperCall = false;
    for (Chain c : chains) {
      List<Chain.Segment> segs = c.segments();
      for (int i = 0; i + 1 < segs.size(); i++) {
        if (segs.get(i) instanceof Chain.Name name && segs.get(i + 1) instanceof Chain.Call call) {
          callNames.put(call.args(), name.token().text());
          String text = name.token().text();
          helperCall |= HELPER.matcher(text).matches() || helpers.contains(text);
        }
      }
    }
    List<JsTrees.Fn> fns = JsTrees.functions(body);
    boolean nested = false;
    boolean repeated = false;
    for (Chain a : asserts) {
      for (JsTrees.Fn fn : fns) {
        if (inside(a.first(), fn.body())) {
          nested = true;
          repeated |= !(fn.start() == 0 && ONCE_CALLBACKS.contains(callNames.getOrDefault(fn.container(), "")));
        }
      }
    }
    boolean loop = false;
    boolean branch = false;
    for (Token t : Nodes.tokens(body)) {
      loop |= t.kind() == TokenKind.IDENT && LOOPS.contains(t.text());
      branch |= (t.kind() == TokenKind.IDENT || t.kind() == TokenKind.OP) && BRANCHES.contains(t.text());
    }
    boolean exact = actual > expected ? !loop && !branch && !nested : !loop && !helperCall && !repeated;
    if (exact) {
      ctx.report(counter.first(), counter.last(), "\"expect.assertions(" + expected + ")\" does not match the " + actual
        + (actual == 1 ? " assertion" : " assertions") + " this test makes; set it to " + actual + ".");
    }
  }

  private static boolean inside(Token t, Node.Group g) {
    Token from = g.first();
    Token to = g.last();
    return from != null && to != null && compare(from, t) <= 0 && compare(t, to) <= 0;
  }

  private static int compare(Token a, Token b) {
    return a.line() != b.line() ? Integer.compare(a.line(), b.line()) : Integer.compare(a.column(), b.column());
  }

  /**
   * Names that may hide assertions: functions declared in the file that assert, and names imported from test helper
   * modules (a path with test, spec, helper, util, fixture, support, assert, expect or mock in it, test libraries
   * left out).
   */
  private static Set<String> helpers(Node.Group root, Set<String> keywords) {
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
    // import { a, b as c } from './helpers'; import d from 'test-utils'
    List<Node> kids = root.children();
    for (int i = 0; i < kids.size(); i++) {
      if (!Nodes.isIdent(kids.get(i), "import")) {
        continue;
      }
      List<Token> names = new ArrayList<>();
      int j = i + 1;
      while (j < kids.size() && !Nodes.isIdent(kids.get(j), "from") && !Nodes.isLeaf(kids.get(j), ";")) {
        for (Token t : Nodes.tokens(kids.get(j))) {
          if (t.kind() == TokenKind.IDENT) {
            names.add(t);
          }
        }
        j++;
      }
      Token path = j + 1 < kids.size() && Nodes.isIdent(kids.get(j), "from") ? Nodes.leaf(kids.get(j + 1)) : null;
      if (path != null && path.kind() == TokenKind.STRING && TEST_MODULE.matcher(path.text()).matches()
        && !TEST_LIBRARY.matcher(path.text()).matches()) {
        names.forEach(t -> out.add(t.text()));
      }
    }
    return out;
  }
}
