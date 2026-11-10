package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Helpers over Dart token trees for the extractor and the Dart rules. */
public final class DartTrees {

  private static final Set<String> CONTINUES = Set.of("else", "catch", "finally", "on", "while");

  private DartTrees() {
  }

  /**
   * A closure {@code (params) async {...}} or {@code (params) => expr}. {@code body} is the "{}" group, or for an arrow
   * closure a bracketless group holding the expression. {@code to} is the index (exclusive) where it ends in the list.
   */
  public record Closure(Node.Group params, boolean async, boolean arrow, Node.Group body, int from, int to) {
  }

  /** The closure that starts at {@code nodes[i]}, or null. An arrow body ends at a top-level "," or ";". */
  public static Closure closureAt(List<Node> nodes, int i) {
    if (i >= nodes.size() || !(nodes.get(i) instanceof Node.Group params) || !params.is("(")) {
      return null;
    }
    int j = i + 1;
    boolean async = false;
    if (j < nodes.size() && (Nodes.isIdent(nodes.get(j), "async") || Nodes.isIdent(nodes.get(j), "sync"))) {
      async = Nodes.isIdent(nodes.get(j), "async");
      j++;
      if (j < nodes.size() && Nodes.isLeaf(nodes.get(j), "*")) {
        j++;
      }
    }
    if (j < nodes.size() && nodes.get(j) instanceof Node.Group body && body.is("{")) {
      return new Closure(params, async, false, body, i, j + 1);
    }
    if (j < nodes.size() && Nodes.isLeaf(nodes.get(j), "=>")) {
      int k = j + 1;
      while (k < nodes.size() && !Nodes.isLeaf(nodes.get(k), ",") && !Nodes.isLeaf(nodes.get(k), ";")) {
        k++;
      }
      return new Closure(params, async, true, new Node.Group(null, null, List.copyOf(nodes.subList(j + 1, k))), i, k);
    }
    return null;
  }

  /** The closure an argument is ({@code () {...}}, {@code (x) => y}), or null. */
  public static Closure closure(List<Node> item) {
    Closure c = closureAt(item, 0);
    return c != null && c.to() == item.size() ? c : null;
  }

  /** Named arguments of a call, {@code name: value}, in order. */
  public static Map<String, List<Node>> namedArgs(Node.Group args) {
    Map<String, List<Node>> out = new LinkedHashMap<>();
    for (List<Node> item : args.items()) {
      if (isNamed(item)) {
        out.put(Nodes.leaf(item.get(0)).text(), item.subList(2, item.size()));
      }
    }
    return out;
  }

  /** Positional arguments of a call (named ones left out). */
  public static List<List<Node>> positionalArgs(Node.Group args) {
    return args.items().stream().filter(item -> !isNamed(item)).toList();
  }

  private static boolean isNamed(List<Node> item) {
    Token t = Nodes.leaf(item.get(0));
    return item.size() >= 2 && t != null && t.kind() == TokenKind.IDENT && Nodes.isLeaf(item.get(1), ":");
  }

  public static boolean isAwaited(Chain c) {
    return c.precededBy("await");
  }

  /** Returned by {@code return x} or as the body of an arrow function. */
  public static boolean isReturned(Chain c) {
    return c.precededBy("return") || c.precededBy("=>");
  }

  /**
   * Statements of a block: children split on ";" and after nested "{}" blocks ({@code if (a) {...}} ends there unless
   * {@code else}, {@code catch}, {@code on}, {@code finally} or {@code while} follows). Empty parts are dropped.
   */
  public static List<List<Node>> statements(Node.Group block) {
    List<List<Node>> out = new ArrayList<>();
    List<Node> part = new ArrayList<>();
    List<Node> kids = block.children();
    for (int i = 0; i < kids.size(); i++) {
      Node n = kids.get(i);
      if (Nodes.isLeaf(n, ";")) {
        flush(out, part);
        part = new ArrayList<>();
        continue;
      }
      part.add(n);
      if (n instanceof Node.Group g && g.is("{") && endsStatement(kids, i)) {
        flush(out, part);
        part = new ArrayList<>();
      }
    }
    flush(out, part);
    return out;
  }

  private static boolean endsStatement(List<Node> kids, int i) {
    if (i + 1 >= kids.size()) {
      return true;
    }
    Token next = Nodes.leaf(kids.get(i + 1));
    if (next == null) {
      return true;
    }
    // a map or set literal, or a closure, inside an expression: `x = {...};`, `f = () {...};`, `{...}.forEach(...)`
    return !CONTINUES.contains(next.text()) && !next.is(";") && !next.is(".") && !next.is("?.") && !next.is("..")
      && !next.is(",") && !next.is(")");
  }

  private static void flush(List<List<Node>> out, List<Node> part) {
    if (!part.isEmpty()) {
      out.add(List.copyOf(part));
    }
  }

  /** The text of a string literal without prefix and quotes ({@code r'a$b'} gives {@code a$b}), interpolation kept. */
  public static String stringValue(Token t) {
    String s = t.text();
    if (s.startsWith("r")) {
      s = s.substring(1);
    }
    if (s.isEmpty()) {
      return s;
    }
    int q = s.length() >= 6 && (s.startsWith("'''") || s.startsWith("\"\"\"")) ? 3 : 1;
    String quote = s.substring(0, q);
    int end = s.length() >= 2 * q && s.endsWith(quote) ? s.length() - q : s.length();
    return s.substring(Math.min(q, end), end);
  }
}
