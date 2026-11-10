package io.github.snakeinprod.testquality.rules;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every rule with its metadata. {@code family} is "python" (repository testquality-python, language py), "js"
 * (repositories testquality-js and testquality-ts, languages js and ts) or "dart" (testquality-dart, language dart).
 * {@code active}: in the built-in profile. A rule's longer description, when it has one, is
 * {@code /rules/<family>/<key>.html} on the classpath and replaces the generated one.
 */
public final class RuleCatalog {

  private RuleCatalog() {
  }

  public record Spec(String family, String key, String severity, boolean active, String name, String description, String example,
    Supplier<TestRule> factory) {

    public String html() {
      try (var in = RuleCatalog.class.getResourceAsStream("/rules/" + family + "/" + key + ".html")) {
        if (in != null) {
          return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
      } catch (java.io.IOException e) {
        // fall back to the generated description
      }
      return "<p>" + escape(description) + "</p>\n<h2>Noncompliant code example</h2>\n<pre>\n" + escape(example) + "\n</pre>\n";
    }

    private static String escape(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
  }

  private static Spec spec(String family, String key, String severity, boolean active, String name, String description, String example,
    Supplier<TestRule> factory) {
    return new Spec(family, key, severity, active, name, description, example, factory);
  }

  public static final List<Spec> ALL = List.of(
    spec("python", "MockAssertionTypo", "CRITICAL", true, "Mock assertion methods should be spelled correctly",
      "A misspelled assert method on a mock, or a non-assert method used as one (called_once_with), is accepted by a plain Mock and silently checks nothing.",
      "m.assert_called_onec()", io.github.snakeinprod.testquality.rules.python.MockAssertionTypo::new),
    spec("python", "MockAttributeAsAssertion", "MAJOR", true, "Mock attributes should not be used as assertions",
      "An attribute or method of a mock used in an assert is always truthy, so the assertion never fails.",
      "assert m.assert_called", io.github.snakeinprod.testquality.rules.python.MockAttributeAsAssertion::new),
    spec("python", "ShadowedTest", "CRITICAL", true, "Test names should be unique in a module or class",
      "A second test with the same name replaces the first one, which then never runs.",
      "def test_login(): ...\ndef test_login(): ...  # Noncompliant", io.github.snakeinprod.testquality.rules.python.ShadowedTest::new),
    spec("python", "UndiscoveredTest", "MAJOR", true, "Tests should be named so the runner collects them",
      "A function or class with assertions that pytest or unittest never collects because of its name.",
      "def check_login():  # Noncompliant\n    assert login()", io.github.snakeinprod.testquality.rules.python.UndiscoveredTest::new),
    spec("python", "ReturnInsteadOfAssert", "MAJOR", true, "Tests should assert their comparisons, not return them",
      "A test that returns its comparison passes whatever the result is.",
      "def test_sum():\n    return add(1, 2) == 3  # Noncompliant", io.github.snakeinprod.testquality.rules.python.ReturnInsteadOfAssert::new),
    spec("python", "AssertionInsideRaises", "MAJOR", true, "Assertions should not follow the raising call inside pytest.raises",
      "Statements after the call that raises inside a pytest.raises block never run.",
      "with pytest.raises(ValueError):\n    parse(x)\n    assert called  # Noncompliant", io.github.snakeinprod.testquality.rules.python.AssertionInsideRaises::new),
    spec("python", "RaisesTooBroad", "MAJOR", true, "pytest.raises should name the exception and wrap one call",
      "pytest.raises(Exception), or a block with several calls, lets the wrong line raise and the test still pass.",
      "with pytest.raises(Exception):  # Noncompliant\n    setup()\n    act()", io.github.snakeinprod.testquality.rules.python.RaisesTooBroad::new),
    spec("python", "PatchWhereDefined", "MAJOR", true, "mock.patch should target the module that uses the name",
      "Patching the module that defines a name has no effect on code that imported the name with from-import.",
      "patch('lib.clock.now')  # Noncompliant: app.py did from lib.clock import now", io.github.snakeinprod.testquality.rules.python.PatchWhereDefined::new),
    spec("python", "MockNeverUsed", "MINOR", false, "Patched mocks should be used by the test",
      "A patched or created mock that the test neither passes on nor asserts on.",
      "@patch('app.mail')\ndef test_x(mail):  # Noncompliant\n    assert run()", io.github.snakeinprod.testquality.rules.python.MockNeverUsed::new),
    spec("python", "SleepInTest", "MINOR", false, "Tests should not sleep",
      "time.sleep in a test makes it slow, and flaky when it waits for something.",
      "time.sleep(2)  # Noncompliant", io.github.snakeinprod.testquality.rules.python.SleepInTest::new),
    spec("python", "UnseededRandom", "MINOR", false, "Random values in tests should be seeded",
      "random or uuid4 values feeding an assertion without a fixed seed make the test flaky.",
      "assert f(random.randint(1, 9)) > 0  # Noncompliant", io.github.snakeinprod.testquality.rules.python.UnseededRandom::new),
    spec("python", "ComparisonWithoutAssert", "MAJOR", true, "Comparisons in tests should be asserted",
      "A comparison statement whose result is dropped checks nothing.",
      "result == 3  # Noncompliant", io.github.snakeinprod.testquality.rules.python.ComparisonWithoutAssert::new),
    spec("python", "ExceptSwallowsAssertion", "MAJOR", true, "Assertions should not be caught",
      "try / except around an assertion catches its failure, so the test passes.",
      "try:\n    assert x\nexcept Exception:  # Noncompliant\n    pass", io.github.snakeinprod.testquality.rules.python.ExceptSwallowsAssertion::new),
    spec("python", "ParametrizeMismatch", "MAJOR", true, "pytest.mark.parametrize should match the test parameters",
      "Names that do not match the test parameters, or rows of the wrong length, make pytest error or test the wrong thing.",
      "@pytest.mark.parametrize('a,b', [(1,), (2, 3)])  # Noncompliant", io.github.snakeinprod.testquality.rules.python.ParametrizeMismatch::new),
    spec("js", "ExpectWithoutMatcher", "CRITICAL", true, "expect() should be finished with a matcher",
      "An expect that is never finished with a matcher checks nothing.",
      "expect(user.name); // Noncompliant", io.github.snakeinprod.testquality.rules.js.ExpectWithoutMatcher::new),
    spec("js", "UnawaitedAsyncAssertion", "CRITICAL", true, "Async assertions should be awaited or returned",
      "A .resolves / .rejects or Playwright expect that is neither awaited nor returned is not waited for, so its failure is lost.",
      "expect(fetchUser()).resolves.toEqual(u); // Noncompliant", io.github.snakeinprod.testquality.rules.js.UnawaitedAsyncAssertion::new),
    spec("js", "AssertionInUnawaitedCallback", "MAJOR", true, "Assertions should not sit in callbacks the test does not wait for",
      "An assertion inside .then, setTimeout or an event handler that the test does not wait for may never run.",
      "p.then(r => expect(r).toBe(1)); // Noncompliant", io.github.snakeinprod.testquality.rules.js.AssertionInUnawaitedCallback::new),
    spec("js", "AlwaysTruthyMatcher", "MAJOR", true, "Matchers should be able to fail",
      "A matcher that cannot fail on the value given checks nothing.",
      "expect([]).toBeTruthy(); // Noncompliant", io.github.snakeinprod.testquality.rules.js.AlwaysTruthyMatcher::new),
    spec("js", "ShadowedTest", "CRITICAL", true, "Test names should be unique in a describe block",
      "Two tests with the same name in one describe: reports and filters hit only one.",
      "it('saves the user', ...);\nit('saves the user', ...); // Noncompliant", io.github.snakeinprod.testquality.rules.js.ShadowedTest::new),
    spec("js", "SnapshotOnlyTest", "MINOR", false, "Tests should not rely on a snapshot alone",
      "A test whose only assertion is a snapshot accepts whatever was recorded first.",
      "expect(tree).toMatchSnapshot(); // Noncompliant", io.github.snakeinprod.testquality.rules.js.SnapshotOnlyTest::new),
    spec("js", "ExpectAssertionsMismatch", "MAJOR", true, "expect.assertions(n) should match the assertions the test can reach",
      "A count that does not match the expect calls makes the test fail for the wrong reason or hides a skipped assertion.",
      "expect.assertions(2); // Noncompliant: one expect", io.github.snakeinprod.testquality.rules.js.ExpectAssertionsMismatch::new),
    spec("js", "SyncQueryForAsyncUi", "MAJOR", true, "Use findBy queries for UI that renders after an action",
      "A getBy or queryBy right after an action that renders later fails or checks the old screen.",
      "click(btn); getByText('Saved'); // Noncompliant", io.github.snakeinprod.testquality.rules.js.SyncQueryForAsyncUi::new),
    spec("js", "WaitForTimeout", "MINOR", false, "Tests should not wait for a fixed time",
      "page.waitForTimeout or a fixed setTimeout delay makes tests slow and flaky.",
      "await page.waitForTimeout(3000); // Noncompliant", io.github.snakeinprod.testquality.rules.js.WaitForTimeout::new),
    spec("js", "MockNeverAsserted", "MINOR", false, "Mock functions passed in should be asserted on",
      "A jest.fn() or vi.fn() created and passed in but never asserted on.",
      "const onSave = jest.fn(); // Noncompliant", io.github.snakeinprod.testquality.rules.js.MockNeverAsserted::new),
    spec("js", "DoneAndPromise", "MAJOR", true, "Tests should not take done and return a promise",
      "A test that takes done and is async or returns a promise: Jest fails or waits on the wrong one.",
      "it('x', async done => { ... }); // Noncompliant", io.github.snakeinprod.testquality.rules.js.DoneAndPromise::new),
    spec("js", "AssertionInLoopNeverRun", "MINOR", false, "Assertions should not run only inside a loop that can be empty",
      "Assertions only inside a loop over a collection that can be empty pass when it is empty.",
      "for (const r of rows) expect(r.ok).toBe(true); // Noncompliant", io.github.snakeinprod.testquality.rules.js.AssertionInLoopNeverRun::new),
    spec("dart", "UnawaitedTesterAction", "CRITICAL", true, "WidgetTester actions should be awaited",
      "A tap, enterText, pump or pumpWidget that is not awaited lets the test check the screen before it changed.",
      "tester.tap(find.byKey(k)); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.UnawaitedTesterAction::new),
    spec("dart", "ExpectWithoutPump", "CRITICAL", true, "Widget expectations should follow a pump after an action",
      "An expect on the widget tree right after an action with no pump in between checks the old frame.",
      "await tester.tap(save);\nexpect(find.text('Saved'), findsOneWidget); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.ExpectWithoutPump::new),
    spec("dart", "UnawaitedExpectLater", "CRITICAL", true, "expectLater should be awaited or returned",
      "expectLater returns a future; not awaited, the test ends before it checks.",
      "expectLater(stream, emits(1)); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.UnawaitedExpectLater::new),
    spec("dart", "AsyncMatcherNotAwaited", "MAJOR", true, "Async matchers should be awaited before the test ends",
      "completion, throwsA or emits on a future or stream in a test that ends before it settles may never check.",
      "expect(load(), completion(isNotEmpty)); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.AsyncMatcherNotAwaited::new),
    spec("dart", "VerifyWithoutCall", "CRITICAL", true, "verify and when should take a call on a mock",
      "A verify or when whose argument is not a call on a mock checks nothing.",
      "verify(mock.save); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.VerifyWithoutCall::new),
    spec("dart", "StubNeverUsed", "MINOR", false, "Stubs should be used by the test",
      "when(...).thenReturn(...) stubs that the test never triggers.",
      "when(() => repo.load()).thenReturn(x); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.StubNeverUsed::new),
    spec("dart", "MockNeverVerified", "MINOR", false, "Mocks passed in should be verified or stubbed",
      "A mock created and injected with no verify and no stub that affects an assertion.",
      "final api = MockApi(); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.MockNeverVerified::new),
    spec("dart", "TrivialMatcher", "MAJOR", true, "Matchers should be able to fail",
      "A matcher that cannot fail on the value, such as anything.",
      "expect(x, anything); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.TrivialMatcher::new),
    spec("dart", "GoldenOnlyTest", "MINOR", false, "Widget tests should not rely on a golden file alone",
      "A widget test whose only check is matchesGoldenFile.",
      "await expectLater(find.byType(App), matchesGoldenFile('a.png')); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.GoldenOnlyTest::new),
    spec("dart", "DuplicateTestName", "MAJOR", true, "Test descriptions should be unique in a group",
      "Two tests with the same description in one group: filters and reports hit both or the wrong one.",
      "testWidgets('shows error', ...);\ntestWidgets('shows error', ...); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.DuplicateTestName::new),
    spec("dart", "RealDelayInTest", "MINOR", false, "Widget tests should not wait for real time",
      "Future.delayed, sleep or a real Timer in a widget test instead of tester.pump(duration) or fake async.",
      "await Future.delayed(Duration(seconds: 1)); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.RealDelayInTest::new),
    spec("dart", "SkipWithoutReason", "MINOR", false, "Skipped tests should say why",
      "skip: true without a reason string.",
      "test('x', () {}, skip: true); // Noncompliant", io.github.snakeinprod.testquality.rules.dart.SkipWithoutReason::new));

  public static List<Spec> family(String family) {
    return ALL.stream().filter(s -> s.family().equals(family)).toList();
  }

  /** Repository key for a SonarQube language key. */
  public static String repository(String language) {
    return switch (language) {
      case "py" -> "testquality-python";
      case "js" -> "testquality-js";
      case "ts" -> "testquality-ts";
      case "dart" -> "testquality-dart";
      default -> throw new IllegalArgumentException(language);
    };
  }

  /** Rule family of a SonarQube language key. */
  public static String familyOf(String language) {
    return switch (language) {
      case "py" -> "python";
      case "js", "ts" -> "js";
      default -> language;
    };
  }
}
