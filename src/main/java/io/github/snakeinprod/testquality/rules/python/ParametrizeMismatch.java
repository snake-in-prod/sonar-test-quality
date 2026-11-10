package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Names that do not match the test parameters, or rows of the wrong length, make pytest error or test the wrong thing.
 * Only literal names and literal rows are checked. A name missing from the signature is reported only when it is a typo
 * of a parameter, or when the test takes no parameters and the file has no autouse or usefixtures fixtures (a
 * parametrized name may legally feed a fixture the test uses).
 */
public class ParametrizeMismatch implements TestRule {

  /** A parametrize decorator: its call arguments, its literal names and where they are written. */
  private record Mark(List<List<Node>> args, List<String> names, Token from, Token to, String label) {
  }

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null) {
      return;
    }
    boolean fixturesAround = ctx.file().tokens().stream().anyMatch(t -> t.isIdent("usefixtures") || t.isIdent("autouse"));
    for (TestCase t : ctx.tests().tests()) {
      PyStatement def = (PyStatement) t.body();
      List<Mark> marks = new ArrayList<>();
      for (PyStatement d : def.decorators()) {
        List<Chain> chains = Chains.direct(d.group(), Chains.keywords("py"));
        if (chains.size() == 1 && chains.get(0).segments().size() >= 2 && chains.get(0).endsWithCall()
          && m.resolve(head(chains.get(0))).equals("pytest.mark.parametrize")) {
          List<List<Node>> args = chains.get(0).calls().get(0).items();
          List<Node> argnames = arg(args, 0, "argnames");
          List<String> names = argnames == null ? null : names(argnames);
          if (names != null) {
            Token from = argnames.get(0).first();
            Token to = argnames.get(argnames.size() - 1).last();
            marks.add(new Mark(args, names, from, to, ComparisonWithoutAssert.code(ctx.file(), from, to)));
          }
        }
      }
      Set<String> covered = new HashSet<>();
      marks.forEach(k -> covered.addAll(k.names()));
      List<String> params = def.params().stream().filter(p -> !p.varArgs() && !p.kwArgs()).map(PyStatement.Param::name)
        .filter(p -> !p.equals("self") && !p.equals("cls")).toList();
      boolean opaque = def.params().stream().anyMatch(p -> p.varArgs() || p.kwArgs());
      for (Mark k : marks) {
        if (!opaque && !hasKeyword(k.args(), "indirect")) {
          checkNames(ctx, t, k, params, covered, fixturesAround);
        }
        checkRows(ctx, m, k);
      }
    }
  }

  private static void checkNames(RuleContext ctx, TestCase t, Mark k, List<String> params, Set<String> covered, boolean fixturesAround) {
    List<String> absent = new ArrayList<>();
    for (String n : k.names()) {
      if (params.contains(n)) {
        continue;
      }
      String typo = params.stream().filter(p -> !covered.contains(p) && close(p, n)).findFirst().orElse(null);
      if (typo != null) {
        ctx.report(k.from(), k.to(), "\"" + n + "\" is not a parameter of \"" + t.name() + "\"; did you mean \"" + typo + "\"?");
      } else {
        absent.add("\"" + n + "\"");
      }
    }
    if (params.isEmpty() && !fixturesAround && !absent.isEmpty()) {
      ctx.report(k.from(), k.to(), "\"" + t.name() + "\" has no parameter " + String.join(", ", absent)
        + "; add " + (absent.size() == 1 ? "it" : "them") + " to the signature or fix the names.");
    }
  }

  /** Rows that are literal tuples, lists or pytest.param(...) must have one value per name. */
  private static void checkRows(RuleContext ctx, PyModule m, Mark k) {
    List<Node> values = arg(k.args(), 1, "argvalues");
    if (values == null || values.size() != 1 || !(values.get(0) instanceof Node.Group list) || list.is("{")
      || list.children().stream().anyMatch(x -> Nodes.isIdent(x, "for"))) {
      return;
    }
    int want = k.names().size();
    List<Token[]> wrong = new ArrayList<>();
    int checked = 0;
    int sameCount = -1;
    for (List<Node> row : list.items()) {
      Integer len = rowLength(m, row, want);
      if (len == null) {
        continue;
      }
      checked++;
      if (len != want) {
        wrong.add(new Token[] {row.get(0).first(), row.get(row.size() - 1).last()});
        sameCount = sameCount == -1 || sameCount == len ? len : -2;
      }
    }
    if (wrong.isEmpty()) {
      return;
    }
    String names = k.label();
    if (wrong.size() == checked && checked > 1 && sameCount >= 0) {
      ctx.report(k.from(), k.to(), names + " names " + want + " parameter" + (want == 1 ? "" : "s") + " but every row has "
        + sameCount + " value" + (sameCount == 1 ? "" : "s") + "; make the names and the rows match.");
      return;
    }
    for (Token[] r : wrong) {
      ctx.report(r[0], r[1], "This row does not have " + want + " value" + (want == 1 ? "" : "s") + " for " + names
        + "; give it one value per name.");
    }
  }

  /** The length of a literal row, or null when it cannot be counted (a variable, a call, a starred item, a single name). */
  private static Integer rowLength(PyModule m, List<Node> row, int want) {
    if (row.size() == 1 && row.get(0) instanceof Node.Group g && (g.is("(") || g.is("[")) && want > 1) {
      List<List<Node>> items = g.items();
      boolean trailingComma = !g.children().isEmpty() && Nodes.isLeaf(g.children().get(g.children().size() - 1), ",");
      if (g.is("(") && items.size() == 1 && !trailingComma || items.stream().anyMatch(ParametrizeMismatch::starred)
        || g.children().stream().anyMatch(x -> Nodes.isIdent(x, "for"))) {
        return null;
      }
      return items.size();
    }
    List<Chain> chains = Chains.direct(new Node.Group(null, null, row), Chains.keywords("py"));
    if (chains.size() == 1 && chains.get(0).from() == 0 && chains.get(0).to() == row.size() && chains.get(0).segments().size() >= 2
      && chains.get(0).calls().size() == 1 && chains.get(0).endsWithCall()
      && m.resolve(head(chains.get(0))).equals("pytest.param")) {
      List<List<Node>> items = chains.get(0).calls().get(0).items();
      if (items.stream().anyMatch(ParametrizeMismatch::starred)) {
        return null;
      }
      return (int) items.stream().filter(i -> !isKeyword(i)).count();
    }
    return null;
  }

  private static boolean starred(List<Node> item) {
    return Nodes.isLeaf(item.get(0), "*") || Nodes.isLeaf(item.get(0), "**");
  }

  /** The positional argument at {@code index}, or the keyword argument {@code name}, or null. */
  private static List<Node> arg(List<List<Node>> args, int index, String name) {
    for (List<Node> a : args) {
      if (isKeyword(a) && Nodes.isIdent(a.get(0), name)) {
        return a.subList(2, a.size());
      }
    }
    return index < args.size() && !isKeyword(args.get(index)) && args.stream().limit(index).noneMatch(ParametrizeMismatch::isKeyword)
      ? args.get(index) : null;
  }

  private static boolean isKeyword(List<Node> item) {
    return item.size() > 2 && Nodes.leaf(item.get(0)) != null && Nodes.leaf(item.get(0)).kind() == TokenKind.IDENT
      && Nodes.isLeaf(item.get(1), "=");
  }

  private static boolean hasKeyword(List<List<Node>> args, String name) {
    return args.stream().anyMatch(a -> isKeyword(a) && Nodes.isIdent(a.get(0), name));
  }

  /**
   * The names of a literal argnames: "a, b," or 'a' or ("a", "b") / ["a", "b"]; null for anything else (a variable, an
   * f-string, a concatenation) or a part that is not an identifier.
   */
  private static List<String> names(List<Node> nodes) {
    if (nodes.size() != 1) {
      return null;
    }
    List<String> out = new ArrayList<>();
    if (nodes.get(0) instanceof Node.Group g && (g.is("(") || g.is("["))) {
      for (List<Node> item : g.items()) {
        String one = item.size() == 1 ? literal(Nodes.leaf(item.get(0))) : null;
        if (one == null) {
          return null;
        }
        out.add(one.strip());
      }
    } else {
      String text = literal(Nodes.leaf(nodes.get(0)));
      if (text == null) {
        return null;
      }
      for (String p : text.split(",")) {
        if (!p.isBlank()) {
          out.add(p.strip());
        }
      }
    }
    boolean valid = out.stream().allMatch(n -> n.matches("[A-Za-z_]\\w*")) && out.stream().distinct().count() == out.size();
    return valid && !out.isEmpty() ? out : null;
  }

  /** The content of a plain one-line string literal ("a", 'a', r"a"), or null. */
  private static String literal(Token t) {
    if (t == null || t.kind() != TokenKind.STRING || !t.text().matches("(?i)[ru]?(\"[^\"]*\"|'[^']*')")) {
      return null;
    }
    String s = t.text().replaceFirst("(?i)^[ru]?.", "");
    return s.substring(0, s.length() - 1);
  }

  /** The names before the first call: "pytest.mark.parametrize". */
  private static String head(Chain c) {
    List<String> out = new ArrayList<>();
    for (Chain.Segment s : c.segments()) {
      if (!(s instanceof Chain.Name n)) {
        break;
      }
      out.add(n.token().text());
    }
    return String.join(".", out);
  }

  /** A likely typo: edit distance 1 or 2, both names longer than 3 characters. */
  private static boolean close(String a, String b) {
    if (a.length() <= 3 || b.length() <= 3) {
      return false;
    }
    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      }
      int[] x = prev;
      prev = cur;
      cur = x;
    }
    return prev[b.length()] <= 2;
  }
}
