package io.github.snakeinprod.testquality.rules.python;

import io.github.snakeinprod.testquality.lang.python.PyModule;
import io.github.snakeinprod.testquality.lang.python.PyStatement;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import io.github.snakeinprod.testquality.rules.RuleContext;
import io.github.snakeinprod.testquality.rules.TestRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A function or class with assertions that pytest or unittest never collects because of its name. Only flags code that
 * cannot be a helper: no parameters, no decorator, not referenced anywhere in the project, in a file that has collected
 * tests (so the project uses the default name patterns).
 */
public class UndiscoveredTest implements TestRule {

  /** xunit setup and teardown methods, unittest's runTest: run by the framework under their own names. */
  private static final Set<String> FRAMEWORK = Set.of("setup", "teardown", "setUp", "tearDown", "setUpClass", "tearDownClass",
    "setUpModule", "tearDownModule", "asyncSetUp", "asyncTearDown", "setup_method", "teardown_method", "setup_function",
    "teardown_function", "setup_module", "teardown_module", "setup_class", "teardown_class", "runTest");
  private static final Set<String> FIXTURE_LIKE = Set.of("Mixin", "Base", "Abstract", "Helper", "Mock", "Fake", "Stub");

  @Override
  public void check(RuleContext ctx) {
    PyModule m = ctx.file().get(PyModule.class);
    if (m == null || ctx.file().path().endsWith("conftest.py")) {
      return;
    }
    boolean collects = !ctx.tests().tests().isEmpty();
    for (PyStatement s : m.statements()) {
      if (collects && s.keyword().equals("def")) {
        function(ctx, m, s, false);
      } else if (s.keyword().equals("class") && s.name() != null) {
        testClass(ctx, m, s, collects);
      }
    }
  }

  private static void testClass(RuleContext ctx, PyModule m, PyStatement cls, boolean collects) {
    String name = cls.name();
    if (isUnittest(m, cls)) {
      cls.body().stream().filter(s -> s.keyword().equals("def")).forEach(s -> function(ctx, m, s, true));
      return;
    }
    List<PyStatement> tests = cls.body().stream()
      .filter(s -> s.keyword().equals("def") && s.name() != null && s.name().startsWith("test") && hasPlainAssert(s)).toList();
    PyStatement init = cls.body().stream().filter(s -> s.keyword().equals("def") && "__init__".equals(s.name())).findFirst().orElse(null);
    if (name.startsWith("Test")) {
      if (init != null && !tests.isEmpty()) {
        ctx.report(init.nameToken(), "pytest does not collect \"" + name
          + "\" because it defines __init__; move the setup to setup_method or a fixture.");
      } else if (init == null && collects) {
        cls.body().stream().filter(s -> s.keyword().equals("def")).forEach(s -> function(ctx, m, s, true));
      }
    } else if (collects && init == null && !tests.isEmpty() && plainBases(cls) && FIXTURE_LIKE.stream().noneMatch(name::contains)
      && !referenced(ctx, name, cls.nameToken())) {
      ctx.report(cls.nameToken(), "pytest never collects \"" + name
        + "\" because its name does not start with \"Test\"; rename it so it does.");
    }
  }

  /** A function or method with assertions whose name the runner does not collect. */
  private static void function(RuleContext ctx, PyModule m, PyStatement def, boolean method) {
    String name = def.name();
    if (name == null || name.startsWith("test") || name.startsWith("_") || name.startsWith("pytest_") || FRAMEWORK.contains(name)
      || !def.decorators().isEmpty()) {
      return;
    }
    List<PyStatement.Param> params = def.params();
    boolean noParams = method ? params.size() == 1 && params.get(0).name().equals("self") : params.isEmpty();
    if (noParams && hasAssertion(m, def) && !referenced(ctx, name, def.nameToken())) {
      ctx.report(def.nameToken(), "\"" + name + "\" has assertions but is never run because its name does not start with "
        + "\"test\"; rename it so it does.");
    }
  }

  private static boolean isUnittest(PyModule m, PyStatement cls) {
    return cls.bases().stream().anyMatch(b -> m.resolve(b).endsWith("TestCase"));
  }

  private static boolean plainBases(PyStatement cls) {
    return cls.bases().isEmpty() || cls.bases().equals(List.of("object"));
  }

  private static boolean hasPlainAssert(PyStatement def) {
    return def.body().stream().flatMap(b -> b.walk().stream()).anyMatch(s -> s.keyword().equals("assert"));
  }

  /** An assert statement, {@code self.assert*(...)} / {@code self.fail(...)} or {@code pytest.raises(...)}. */
  private static boolean hasAssertion(PyModule m, PyStatement def) {
    for (PyStatement b : def.body()) {
      for (PyStatement s : b.walk()) {
        if (s.keyword().equals("assert")) {
          return true;
        }
        for (Chain c : Chains.in(s.group(), Chains.keywords("py"))) {
          List<String> names = c.names();
          String path = m.resolve(c.path());
          if (c.endsWithCall() && names.size() == 2 && names.get(0).equals("self")
            && (names.get(1).startsWith("assert") || names.get(1).equals("fail")) || path.equals("pytest.raises")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** The name is read anywhere in this file (also in a string, for getattr or __all__) or in any other project file. */
  private static boolean referenced(RuleContext ctx, String name, Token def) {
    Pattern word = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
    for (Token t : ctx.file().tokens()) {
      if (t != def && (t.isIdent(name) || t.kind() == TokenKind.STRING && word.matcher(t.text()).find())) {
        return true;
      }
    }
    String where = projectIdentifiers(ctx).get(name);
    return where != null && !where.equals(ctx.file().path());
  }

  /**
   * Every identifier of the project mapped to the one file that has it, or "*" when several do. Built once, and only
   * when a candidate is found. ponytail: parses every project file once; an import index would be lighter if it shows up.
   */
  private static Map<String, String> projectIdentifiers(RuleContext ctx) {
    return ctx.shared("UndiscoveredTest.identifiers", k -> {
      Map<String, String> out = new HashMap<>();
      for (String path : ctx.projectPaths()) {
        ParsedFile f = ctx.project(path);
        if (f == null) {
          continue;
        }
        for (Token t : f.tokens()) {
          if (t.kind() == TokenKind.IDENT) {
            out.merge(t.text(), path, (a, b) -> a.equals(b) ? a : "*");
          }
        }
      }
      return out;
    });
  }
}
