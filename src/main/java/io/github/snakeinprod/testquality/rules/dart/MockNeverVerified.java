package io.github.snakeinprod.testquality.rules.dart;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A mock created in the test and only handed to constructors, in a test that stubs or verifies other mocks: nothing
 * checks how the code uses it. Also holds the mock helpers of {@link StubNeverUsed} and {@link VerifyWithoutCall}.
 */
public class MockNeverVerified implements TestRule {

  /** Calls whose arguments stub, verify or reset a mock. */
  static final Set<String> MOCK_API = Set.of("when", "verify", "verifyNever", "verifyInOrder", "verifyZeroInteractions",
    "verifyNoMoreInteractions", "untilCalled", "reset", "clearInteractions", "throwOnMissingStub");
  static final Set<String> ACCESSORS = Set.of(".", "?.", "..", "?..", "!.");

  /** A mention of a mock variable: the innermost call it is a direct argument of (null when none), inside a mock API call. */
  record Use(Token token, String callee, boolean mockApi) {
  }

  @Override
  public void check(RuleContext ctx) {
    Set<String> classes = mockClasses(ctx.file().root());
    for (TestCase t : ctx.tests().tests()) {
      Node.Group body = t.bodyGroup();
      if (body == null || !interacts(body)) {
        continue;
      }
      localMocks(body, classes).forEach((name, decl) -> {
        List<Use> uses = uses(body, name, decl);
        boolean injectedOnly = !uses.isEmpty() && uses.stream().allMatch(u -> !u.mockApi() && u.callee() != null
          && Character.isUpperCase(u.callee().charAt(0)));
        if (injectedOnly) {
          ctx.report(decl, "\"" + name + "\" is injected but never stubbed or verified; verify how the code uses it, or pass a"
            + " fake if it does not matter.");
        }
      });
    }
  }

  /** The test stubs or verifies some mock: it is an interaction test, not one that only needs dummies. */
  private static boolean interacts(Node.Group body) {
    for (Chain c : Chains.in(body, Chains.keywords("dart"))) {
      if (MOCK_API.contains(c.firstName()) && c.callAfter(0) != null) {
        return true;
      }
    }
    return false;
  }

  /** Classes declared in the file that extend {@code Mock}. */
  static Set<String> mockClasses(Node.Group root) {
    Set<String> out = new HashSet<>();
    List<Node> kids = root.children();
    for (int i = 0; i + 3 < kids.size(); i++) {
      if (Nodes.isIdent(kids.get(i), "class") && Nodes.leaf(kids.get(i + 1)) != null) {
        int j = i + 2;
        if (Nodes.isLeaf(kids.get(j), "<")) {
          while (j < kids.size() && !Nodes.isLeaf(kids.get(j), ">") && !Nodes.isLeaf(kids.get(j), ">>")) {
            j++;
          }
          j++;
        }
        // primary constructor: class MockRepo() extends Mock
        if (j < kids.size() && kids.get(j) instanceof Node.Group params && params.is("(")) {
          j++;
        }
        if (j + 1 < kids.size() && Nodes.isIdent(kids.get(j), "extends") && Nodes.isIdent(kids.get(j + 1), "Mock")) {
          out.add(Nodes.leaf(kids.get(i + 1)).text());
        }
      }
    }
    return out;
  }

  static boolean isMockClass(String name, Set<String> declared) {
    return declared.contains(name) || name.length() > 4 && name.startsWith("Mock") && Character.isUpperCase(name.charAt(4));
  }

  /**
   * {@code final x = MockX();}, {@code var x = ...}, {@code MockX x = ...} declared in the group, name to name token. A
   * {@code MockX(...)} with arguments of a class not declared here is a fake with a handler (package:http's
   * {@code MockClient((request) => ...)}), not a mockito or mocktail mock.
   */
  static Map<String, Token> localMocks(Node.Group scope, Set<String> classes) {
    Map<String, Token> out = new LinkedHashMap<>();
    Nodes.groups(scope, g -> {
      List<Node> k = g.children();
      for (int i = 1; i + 3 < k.size(); i++) {
        Token name = Nodes.leaf(k.get(i));
        Token prev = Nodes.leaf(k.get(i - 1));
        Token cls = Nodes.leaf(k.get(i + 2));
        boolean declares = prev != null && (prev.isIdent("final") || prev.isIdent("var")
          || prev.kind() == TokenKind.IDENT && Character.isUpperCase(prev.text().charAt(0)));
        boolean cascade = i + 4 < k.size() && Nodes.leaf(k.get(i + 4)) != null && ACCESSORS.contains(Nodes.leaf(k.get(i + 4)).text());
        if (declares && name != null && name.kind() == TokenKind.IDENT && Nodes.isLeaf(k.get(i + 1), "=") && cls != null
          && cls.kind() == TokenKind.IDENT && isMockClass(cls.text(), classes) && k.get(i + 3) instanceof Node.Group args
          && args.is("(") && (args.children().isEmpty() || classes.contains(cls.text())) && !cascade) {
          out.putIfAbsent(name.text(), name);
        }
      }
    });
    return out;
  }

  /** Every mention of variable {@code name} in the group except its declaration, members, labels and assignments. */
  static List<Use> uses(Node.Group scope, String name, Token decl) {
    List<Use> out = new ArrayList<>();
    collect(scope, null, false, name, decl, out);
    return out;
  }

  private static void collect(Node.Group g, String callee, boolean inApi, String name, Token decl, List<Use> out) {
    List<Node> k = g.children();
    for (int i = 0; i < k.size(); i++) {
      Node n = k.get(i);
      Token prev = i > 0 ? Nodes.leaf(k.get(i - 1)) : null;
      if (n instanceof Node.Group sub) {
        String c = null;
        boolean api = inApi;
        if (sub.is("(") && prev != null && prev.kind() == TokenKind.IDENT) {
          c = prev.text();
          api |= MOCK_API.contains(c) && !(i > 1 && Nodes.leaf(k.get(i - 2)) != null && ACCESSORS.contains(Nodes.leaf(k.get(i - 2)).text()));
        }
        collect(sub, c, api, name, decl, out);
        continue;
      }
      Token t = Nodes.leaf(n);
      if (!t.isIdent(name) || t == decl || prev != null && ACCESSORS.contains(prev.text())) {
        continue;
      }
      Token next = i + 1 < k.size() ? Nodes.leaf(k.get(i + 1)) : null;
      boolean label = next != null && next.is(":") && (prev == null || prev.is(","));
      if (label || next != null && next.is("=")) {
        continue;
      }
      boolean receiver = next != null && ACCESSORS.contains(next.text());
      out.add(new Use(t, receiver ? null : callee, inApi));
    }
  }
}
