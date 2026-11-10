package io.github.snakeinprod.testquality.lang.python;

import io.github.snakeinprod.testquality.lang.LanguageSupport;
import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.Lexed;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parsing and test extraction for Python: pytest test functions and {@code Test*} classes (without {@code __init__}),
 * unittest {@code TestCase} subclasses (any base whose name ends with "TestCase", or a class of this file that is one).
 */
public class PythonSupport implements LanguageSupport {

  private static final Pattern SKIP = Pattern.compile("pytest\\.mark\\.(skip|skipif|xfail)|unittest\\.(skip\\w*|expectedFailure)");
  private static final Pattern PATCH = Pattern.compile("(unittest\\.)?(mock\\.|mocker\\.)?patch(\\.(object|dict|multiple))?");
  private static final Set<String> HELPER_DECORATORS = Set.of("pytest.fixture", "contextlib.contextmanager",
    "contextlib.asynccontextmanager");
  private static final Set<String> MOCK_CLASSES = Set.of("Mock", "MagicMock", "AsyncMock");

  @Override
  public String family() {
    return "python";
  }

  @Override
  public List<String> languages() {
    return List.of("py");
  }

  @Override
  public ParsedFile parse(String path, String language, String source) {
    Lexed lexed = PythonLexer.lex(source);
    ParsedFile file = new ParsedFile(path, "py", source, lexed.tokens(), lexed.comments());
    file.put(PyModule.class, PyModule.of(file.root()));
    return file;
  }

  @Override
  public TestFile extract(ParsedFile file) {
    PyModule m = file.get(PyModule.class);
    if (m == null) {
      return TestFile.EMPTY;
    }
    List<TestCase> out = new ArrayList<>();
    boolean skipAll = markedSkip(m, m.statements());
    Set<String> testCases = new HashSet<>();
    for (PyStatement s : m.statements()) {
      if (isTestDef(m, s)) {
        out.add(test(m, s, "", skipAll, List.of()));
      } else if (s.keyword().equals("class")) {
        testClass(m, s, "", skipAll, List.of(), testCases, out);
      }
    }
    return out.isEmpty() ? TestFile.EMPTY : new TestFile(List.copyOf(out));
  }

  private void testClass(PyModule m, PyStatement cls, String outer, boolean outerSkip, List<PyStatement> outerDecorators,
    Set<String> testCases, List<TestCase> out) {
    String name = cls.name();
    if (name == null) {
      return;
    }
    boolean unittest = cls.bases().stream().anyMatch(b -> testCases.contains(b) || m.resolve(b).endsWith("TestCase"));
    if (unittest) {
      testCases.add(name);
    }
    boolean pytest = name.startsWith("Test") && cls.body().stream().noneMatch(s -> s.keyword().equals("def") && "__init__".equals(s.name()));
    if (!unittest && !pytest) {
      return;
    }
    String suite = outer.isEmpty() ? name : outer + " > " + name;
    boolean skip = outerSkip || markedSkip(m, cls.body()) || cls.decorators().stream().anyMatch(d -> isSkipMark(m, d));
    List<PyStatement> decorators = new ArrayList<>(outerDecorators);
    decorators.addAll(cls.decorators());
    for (PyStatement s : cls.body()) {
      if (isTestDef(m, s)) {
        out.add(test(m, s, suite, skip, decorators));
      } else if (s.keyword().equals("class")) {
        testClass(m, s, suite, skip, decorators, testCases, out);
      }
    }
  }

  private static TestCase test(PyModule m, PyStatement def, String suite, boolean classSkip, List<PyStatement> classDecorators) {
    boolean skipped = classSkip || def.decorators().stream().anyMatch(d -> isSkipMark(m, d)) || skipsAtStart(m, def);
    int assertions = 0;
    int mocks = 0;
    List<PyStatement> decorators = new ArrayList<>(classDecorators);
    decorators.addAll(def.decorators());
    for (PyStatement d : decorators) {
      mocks += (int) Chains.in(d.group()).stream().filter(c -> isMock(m, c)).count();
    }
    for (PyStatement b : def.body()) {
      for (PyStatement s : b.walk()) {
        if (s.keyword().equals("assert")) {
          assertions++;
        }
        for (Chain c : Chains.in(s.group())) {
          if (isAssertion(m, c)) {
            assertions++;
          } else if (isMock(m, c)) {
            mocks++;
          }
        }
      }
    }
    var start = def.decorators().isEmpty() ? def.first() : def.decorators().get(0).first();
    return new TestCase(def.name(), suite, def.nameToken(), start, def.last(), def, skipped, def.isAsync(), assertions, mocks);
  }

  /**
   * A def named test* that is not a {@code @pytest.fixture} (a fixture named {@code test_app} is not collected) or a
   * {@code @contextmanager} helper such as {@code test_server_running()}.
   */
  private static boolean isTestDef(PyModule m, PyStatement s) {
    return s.keyword().equals("def") && s.name() != null && s.name().startsWith("test")
      && s.decorators().stream().noneMatch(d -> isHelperDecorator(m, d));
  }

  private static boolean isHelperDecorator(PyModule m, PyStatement decorator) {
    List<Chain> chains = Chains.direct(decorator.group());
    return !chains.isEmpty() && HELPER_DECORATORS.contains(m.resolve(String.join(".", head(chains.get(0)))));
  }

  /** {@code self.assert*(...)}, {@code self.fail(...)}, {@code pytest.raises(...)}, {@code pytest.warns(...)}, {@code m.assert_*(...)}. */
  private static boolean isAssertion(PyModule m, Chain c) {
    List<String> head = head(c);
    if (c.calls().isEmpty()) {
      return false;
    }
    if (head.size() == 2 && head.get(0).equals("self") && (head.get(1).startsWith("assert") || head.get(1).equals("fail"))) {
      return true;
    }
    String path = m.resolve(String.join(".", head));
    return path.equals("pytest.raises") || path.equals("pytest.warns") || head.size() >= 2 && head.get(head.size() - 1).startsWith("assert_");
  }

  /** {@code patch(...)} and its variants, {@code mocker.patch*}, {@code Mock()} / {@code MagicMock()} / {@code AsyncMock()}, {@code monkeypatch.setattr}. */
  private static boolean isMock(PyModule m, Chain c) {
    if (c.calls().isEmpty()) {
      return false;
    }
    List<String> head = head(c);
    String path = String.join(".", head);
    return PATCH.matcher(m.resolve(path)).matches() || MOCK_CLASSES.contains(head.get(head.size() - 1))
      || path.equals("monkeypatch.setattr");
  }

  /** A decorator {@code @pytest.mark.skip}, {@code skipif}, {@code xfail}, {@code @unittest.skip*}, {@code expectedFailure}. */
  private static boolean isSkipMark(PyModule m, PyStatement decorator) {
    List<Chain> chains = Chains.direct(decorator.group());
    return !chains.isEmpty() && SKIP.matcher(m.resolve(String.join(".", head(chains.get(0))))).matches();
  }

  /** A {@code pytestmark = ...} line holding a skip mark. */
  private static boolean markedSkip(PyModule m, List<PyStatement> statements) {
    for (PyStatement s : statements) {
      if (s.nodes().size() > 2 && Nodes.isIdent(s.nodes().get(0), "pytestmark") && Nodes.isLeaf(s.nodes().get(1), "=")
        && Chains.in(s.group()).stream().anyMatch(c -> SKIP.matcher(m.resolve(String.join(".", head(c)))).matches())) {
        return true;
      }
    }
    return false;
  }

  /** The first statement of the body (after a docstring) is {@code self.skipTest(...)} or {@code pytest.skip(...)}. */
  private static boolean skipsAtStart(PyModule m, PyStatement def) {
    for (PyStatement s : def.body()) {
      if (s.nodes().size() == 1 && Nodes.leaf(s.nodes().get(0)) != null && Nodes.leaf(s.nodes().get(0)).kind() == TokenKind.STRING) {
        continue;
      }
      List<Chain> chains = Chains.direct(s.group());
      if (chains.isEmpty() || chains.get(0).from() != 0 || chains.get(0).calls().isEmpty()) {
        return false;
      }
      String path = m.resolve(String.join(".", head(chains.get(0))));
      return path.equals("self.skipTest") || path.equals("pytest.skip");
    }
    return false;
  }

  /** The names before the first call: "mock.patch.object" for {@code mock.patch.object(A, "b").start()}. */
  private static List<String> head(Chain c) {
    List<String> out = new ArrayList<>();
    for (Chain.Segment s : c.segments()) {
      if (s instanceof Chain.Name n) {
        out.add(n.token().text());
      } else {
        break;
      }
    }
    return out;
  }
}
