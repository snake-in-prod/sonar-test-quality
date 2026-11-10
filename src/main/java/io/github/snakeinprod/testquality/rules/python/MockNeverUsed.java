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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A patched or created mock that the test neither passes on nor asserts on. A patch given {@code return_value=} or
 * {@code side_effect=} is a stub on purpose, and so is a name starting with "_", "unused" or "ignored".
 */
public class MockNeverUsed implements TestRule {

  static final Set<String> KEYWORDS = Chains.keywords("py");
  private static final Set<String> PATCH = Set.of("patch", "mock.patch", "unittest.mock.patch", "mocker.patch");
  private static final Set<String> FACTORIES = Set.of("Mock", "MagicMock", "AsyncMock", "NonCallableMock", "NonCallableMagicMock",
    "create_autospec");
  private static final Pattern INTENDED = Pattern.compile("_.*|(?i)(unused|ignored?)\\w*");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null) {
      return;
    }
    // class-level patches add arguments after the method's own: the positions are not worth guessing
    Set<PyStatement> inPatchedClass = Collections.newSetFromMap(new IdentityHashMap<>());
    for (PyStatement s : m.walk()) {
      if (s.keyword().equals("class") && s.decorators().stream().map(MockNeverUsed::decorator).anyMatch(c -> c != null && patchKind(m, c) != null)) {
        inPatchedClass.addAll(s.walk());
      }
    }
    for (TestCase t : ctx.tests().tests()) {
      if (!(t.body() instanceof PyStatement def)) {
        continue;
      }
      Use use = new Use(ctx.file().tokens(), def.nodes().get(def.nodes().size() - 1).last(), def.last());
      if (!inPatchedClass.contains(def)) {
        decorators(ctx, m, def, use);
      }
      for (PyStatement s : MockAssertionTypo.statements(t)) {
        assigned(ctx, m, s, use);
        if (s.keyword().equals("with")) {
          with(ctx, m, s, use);
        }
      }
    }
  }

  /** {@code @patch("a.b")} decorators: the bottom one passes the first argument after self. */
  private static void decorators(RuleContext ctx, PyModule m, PyStatement def, Use use) {
    List<PyStatement> injecting = new ArrayList<>();
    Set<PyStatement> stubs = Collections.newSetFromMap(new IdentityHashMap<>());
    for (PyStatement d : def.decorators()) {
      Chain c = decorator(d);
      if (c == null) {
        return;
      }
      String kind = patchKind(m, c);
      if (kind != null) {
        if (c.calls().size() != 1 || !c.endsWithCall()) {
          return;
        }
        if (injects(kind, c.calls().get(0))) {
          injecting.add(d);
          if (configured(c.calls().get(0))) {
            stubs.add(d);
          }
        }
      } else if (!m.resolve(c.path()).startsWith("pytest.mark.")) {
        // another decorator may pass arguments of its own
        return;
      }
    }
    List<PyStatement.Param> params = new ArrayList<>(def.params());
    if (!params.isEmpty() && (params.get(0).name().equals("self") || params.get(0).name().equals("cls"))) {
      params.remove(0);
    }
    if (injecting.isEmpty() || params.size() < injecting.size() || params.stream().anyMatch(p -> p.varArgs() || p.kwArgs())) {
      return;
    }
    for (int k = 0; k < injecting.size(); k++) {
      PyStatement.Param p = params.get(k);
      PyStatement d = injecting.get(injecting.size() - 1 - k);
      if (!stubs.contains(d) && !INTENDED.matcher(p.name()).matches() && !use.used(p.name(), p.token())) {
        String patch = code(d.nodes());
        ctx.report(p.token(), "The mock \"" + p.name() + "\" from " + patch + " is never used; assert on it, or rename it \"_"
          + p.name() + "\" if it only stubs the call.");
      }
    }
  }

  /** {@code m = Mock()}, {@code m = mocker.patch("a.b")}, {@code m = patch("a.b").start()}. */
  private static void assigned(RuleContext ctx, PyModule m, PyStatement s, Use use) {
    List<Node> nodes = s.nodes();
    Token name = nodes.isEmpty() ? null : Nodes.leaf(nodes.get(0));
    if (nodes.size() < 3 || name == null || name.kind() != TokenKind.IDENT || INTENDED.matcher(name.text()).matches() || !Nodes.isLeaf(nodes.get(1), "=")) {
      return;
    }
    Chain c = whole(nodes.subList(2, nodes.size()));
    if (c == null || !c.endsWithCall() || !createsMock(m, c)) {
      return;
    }
    if (!use.used(name.text(), name)) {
      ctx.report(name, "The mock \"" + name.text() + "\" is never used; pass it to the code under test or assert on it, or remove it.");
    }
  }

  private static boolean createsMock(PyModule m, Chain c) {
    List<String> head = head(c);
    int calls = c.calls().size();
    if (FACTORIES.contains(head.get(head.size() - 1))) {
      return calls == 1;
    }
    String kind = patchKind(m, c);
    if (kind == null || !injects(kind, c.calls().get(0)) || configured(c.calls().get(0))) {
      return false;
    }
    return head.get(0).equals("mocker") ? calls == 1 : calls == 2 && c.lastName().equals("start");
  }

  /** {@code with patch("a.b") as m:}, several items and the parenthesized form included. */
  private static void with(RuleContext ctx, PyModule m, PyStatement s, Use use) {
    List<Node> nodes = s.nodes();
    int start = s.isAsync() ? 2 : 1;
    if (nodes.size() <= start + 1) {
      return;
    }
    List<List<Node>> items = Nodes.split(nodes.subList(start, nodes.size() - 1), ",");
    if (items.size() == 1 && items.get(0).size() == 1 && items.get(0).get(0) instanceof Node.Group g && g.is("(")) {
      items = g.items();
    }
    for (List<Node> item : items) {
      int n = item.size();
      Token name = n >= 3 && Nodes.isIdent(item.get(n - 2), "as") ? Nodes.leaf(item.get(n - 1)) : null;
      if (name == null || name.kind() != TokenKind.IDENT || INTENDED.matcher(name.text()).matches()) {
        continue;
      }
      Chain c = whole(item.subList(0, n - 2));
      String kind = c == null ? null : patchKind(m, c);
      if (kind != null && c.calls().size() == 1 && c.endsWithCall() && injects(kind, c.calls().get(0)) && !configured(c.calls().get(0))
        && !use.used(name.text(), name)) {
        ctx.report(name, "The mock \"" + name.text() + "\" bound by " + code(item.subList(0, n - 2))
          + " is never used; assert on it, or drop \"as " + name.text() + "\" if it only stubs the call.");
      }
    }
  }

  /** The chain of a decorator line ("@" then one chain), or null. */
  static Chain decorator(PyStatement d) {
    return whole(d.nodes().subList(1, d.nodes().size()));
  }

  /** The chain covering all the nodes, or null. */
  private static Chain whole(List<Node> nodes) {
    List<Chain> chains = Chains.direct(new Node.Group(null, null, nodes), KEYWORDS);
    return chains.size() == 1 && chains.get(0).from() == 0 && chains.get(0).to() == nodes.size() ? chains.get(0) : null;
  }

  /** "patch" for {@code patch(...)}, {@code mock.patch(...)}, {@code mocker.patch(...)}; "patch.object" for the object forms; else null. */
  static String patchKind(PyModule m, Chain c) {
    List<String> head = head(c);
    if (head.isEmpty() || c.callAfter(head.size() - 1) == null) {
      return null;
    }
    String path = m.resolve(String.join(".", head));
    if (PATCH.contains(path)) {
      return "patch";
    }
    return path.endsWith(".object") && PATCH.contains(path.substring(0, path.length() - ".object".length())) ? "patch.object" : null;
  }

  /** The patch creates the mock itself and passes it on: no replacement given positionally or as new=, no star arguments. */
  private static boolean injects(String kind, Chain.Call call) {
    int positional = 0;
    for (List<Node> item : call.items()) {
      if (Nodes.isLeaf(item.get(0), "*") || Nodes.isLeaf(item.get(0), "**")) {
        return false;
      }
      if (item.size() > 1 && Nodes.isLeaf(item.get(1), "=")) {
        if (Nodes.isIdent(item.get(0), "new")) {
          return false;
        }
      } else {
        positional++;
      }
    }
    return positional == (kind.equals("patch") ? 1 : 2);
  }

  /** The patch sets {@code return_value=} or {@code side_effect=}: the test relies on the stub, not on asserting it. */
  private static boolean configured(Chain.Call call) {
    return call.items().stream().anyMatch(item -> item.size() > 1 && Nodes.isLeaf(item.get(1), "=")
      && (Nodes.isIdent(item.get(0), "return_value") || Nodes.isIdent(item.get(0), "side_effect")));
  }

  /** The names before the first call: "mock.patch.object" for {@code mock.patch.object(A, "b").start()}. */
  static List<String> head(Chain c) {
    List<String> out = new ArrayList<>();
    for (Chain.Segment s : c.segments()) {
      if (!(s instanceof Chain.Name n)) {
        break;
      }
      out.add(n.token().text());
    }
    return out;
  }

  private static String code(List<Node> nodes) {
    StringBuilder sb = new StringBuilder();
    for (Token t : Nodes.tokens(nodes)) {
      sb.append(t.text());
      if (t.is(",")) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  /** Name references in a test: identifiers between the end of the def line and the end of its body, attributes left out. */
  private record Use(List<Token> tokens, Token from, Token to) {

    boolean used(String name, Token except) {
      for (int i = 1; i < tokens.size(); i++) {
        Token t = tokens.get(i);
        if (t != except && t.isIdent(name) && !tokens.get(i - 1).is(".") && after(t, from) && !after(t, to)) {
          return true;
        }
      }
      return false;
    }

    private static boolean after(Token a, Token b) {
      return a.line() != b.line() ? a.line() > b.line() : a.column() > b.column();
    }
  }
}
