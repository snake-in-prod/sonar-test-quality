package io.github.snakeinprod.testquality.lang.js;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Chains;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class JsSupportTest {

  private final JsSupport support = new JsSupport();

  private ParsedFile parse(String name) throws IOException {
    String src = Files.readString(Path.of("src/test/resources/model/js", name));
    return support.parse(name, name.matches(".*\\.tsx?$") ? "ts" : "js", src);
  }

  private List<TestCase> tests(String name) throws IOException {
    return support.extract(parse(name)).tests();
  }

  @Test
  void imported_wrappers_are_test_functions() throws IOException {
    Path root = Path.of("src/test/resources/model/js/wrappers");
    Set<String> paths = paths(root, ".ts");
    Function<String, String> read = reader(root);
    ParsedFile file = support.parse("users.test.ts", "ts", read.apply("users.test.ts"));
    assertThat(support.extract(file)).isSameAs(TestFile.EMPTY);
    // named, default, and re-exported under another name; not through a namespace import
    List<TestCase> tests = support.extract(file, paths, read).tests();
    assertThat(tests).extracting(TestCase::name, TestCase::suite, TestCase::async, TestCase::assertions).containsExactly(
      tuple("sees the profile", "users", true, 1),
      tuple("renames", "users", true, 2),
      tuple("deletes", "users", false, 1));
    assertThat(tests).extracting(t -> t.start().text()).containsExactly("itWithUser", "withUser", "itAsAdmin");
    ParsedFile helper = support.parse("helpers/users.ts", "ts", read.apply("helpers/users.ts"));
    assertThat(support.extract(helper, paths, read)).isSameAs(TestFile.EMPTY);
  }

  @Test
  void jest() throws IOException {
    assertThat(tests("jest.test.js"))
      .extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::async, TestCase::assertions, TestCase::mocks)
      .containsExactly(
        tuple("adds numbers", "Calculator", false, false, 2, 0),
        tuple("uses a spy", "Calculator", false, false, 1, 2),
        tuple("skipped one", "Calculator", true, false, 1, 0),
        tuple("also skipped", "Calculator", true, false, 1, 0),
        tuple("calls back", "Calculator", false, true, 1, 0),
        tuple("resolves", "Calculator", false, true, 1, 0),
        tuple("adds %i + %i", "Calculator", false, false, 1, 0),
        tuple("template table $a + $b", "Calculator", false, false, 1, 0),
        tuple("write this later", "Calculator", true, false, 0, 0),
        tuple("inherits skip", "Calculator > nested", true, false, 1, 0),
        tuple("top level without assertion", "", false, false, 0, 0));
  }

  @Test
  void local_wrappers_are_test_functions() throws IOException {
    List<TestCase> tests = tests("wrapper.test.ts");
    assertThat(tests).extracting(TestCase::name, TestCase::suite, TestCase::async, TestCase::assertions, TestCase::mocks)
      .containsExactly(
        tuple("name", "", false, 1, 0),
        tuple("sees the profile", "users", true, 2, 0),
        tuple("deletes", "users", true, 1, 1),
        tuple("plain", "users", false, 1, 0));
    assertThat(tests.get(1).start().text()).isEqualTo("itWithUser");
    assertThat(tests.get(1).nameToken().line()).isEqualTo(18);
    assertThat(tests.get(1).bodyGroup().is("{")).isTrue();
  }

  @Test
  void jest_positions_and_bodies() throws IOException {
    List<TestCase> t = tests("jest.test.js");
    TestCase first = t.get(0);
    assertThat(first.nameToken().text()).isEqualTo("'adds numbers'");
    assertThat(first.start().text()).isEqualTo("it");
    assertThat(first.start().line()).isEqualTo(10);
    assertThat(first.end().line()).isEqualTo(13);
    assertThat(first.bodyGroup().is("{")).isTrue();
    assertThat(t.get(8).body()).isNull();
    // expression body of an arrow
    TestCase expr = t.get(9);
    assertThat(expr.bodyGroup().open()).isNull();
    assertThat(Chains.in(expr.bodyGroup())).filteredOn(JsTrees::isAssertion).allMatch(JsTrees::isReturned);
  }

  @Test
  void vitest() throws IOException {
    assertThat(tests("vitest.test.ts"))
      .extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::async, TestCase::assertions, TestCase::mocks)
      .containsExactly(
        tuple("parses json", "parser", false, true, 1, 0),
        tuple("mocks module", "parser", false, true, 2, 3),
        tuple("each object $a", "parser", false, false, 1, 0),
        tuple("only one", "parser > inner", false, false, 2, 0));
  }

  @Test
  void testing_library_tsx() throws IOException {
    assertThat(tests("rtl.test.tsx"))
      .extracting(TestCase::name, TestCase::suite, TestCase::async, TestCase::assertions)
      .containsExactly(
        tuple("renders the user's name", "<Greeting />", true, 2),
        tuple("generic helper", "<Greeting />", false, 1));
  }

  @Test
  void playwright() throws IOException {
    TestFile f = support.extract(parse("playwright.spec.ts"));
    assertThat(f.tests())
      .extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::async, TestCase::assertions)
      .containsExactly(
        tuple("signs in", "login", false, true, 2),
        tuple("tagged", "login", false, true, 1),
        tuple("not ready", "login", true, true, 0),
        tuple("broken step", "login > flow", true, true, 0),
        tuple("outside", "", false, true, 1));
    assertThat(f.withoutAssertion()).isZero();
  }

  @Test
  void awaited_returned_and_enclosing_test() throws IOException {
    ParsedFile file = parse("jest.test.js");
    TestFile tests = support.extract(file);
    List<Chain> expects = file.chains().stream().filter(JsTrees::isAssertion).toList();
    Chain resolves = expects.stream().filter(c -> c.names().contains("resolves")).findFirst().orElseThrow();
    assertThat(JsTrees.isAwaited(resolves)).isTrue();
    assertThat(JsTrees.isReturned(resolves)).isFalse();
    Chain inExpressionBody = expects.stream().filter(c -> c.line() == 55).findFirst().orElseThrow();
    assertThat(JsTrees.isReturned(inExpressionBody)).isTrue();
    assertThat(JsTrees.isAwaited(inExpressionBody)).isFalse();
    assertThat(JsTrees.enclosingTest(tests, resolves.container()).name()).isEqualTo("resolves");
    assertThat(JsTrees.enclosingTest(tests, file.root().children().get(0))).isNull();
  }

  @Test
  void function_literals() {
    ParsedFile f = support.parse("a.ts", "ts", "a(async x => 1, function* g(y) { }, async (p, q): Promise<void> => { }, (done) => done(), "
      + "async function () {});");
    List<JsTrees.Fn> fns = JsTrees.functions(f.root());
    assertThat(fns).extracting(JsTrees.Fn::async, JsTrees.Fn::expression, JsTrees.Fn::paramCount)
      .containsExactly(tuple(true, true, 1), tuple(false, false, 1), tuple(true, false, 2), tuple(false, true, 1), tuple(true, false, 0));
    assertThat(fns.get(3).simpleParams()).extracting(t -> t.text()).containsExactly("done");
  }

  /** The project files of a model directory by extension, and a reader of them. */
  static Set<String> paths(Path root, String extension) throws IOException {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(p -> p.toString().endsWith(extension)).map(p -> root.relativize(p).toString().replace('\\', '/'))
        .collect(Collectors.toSet());
    }
  }

  static Function<String, String> reader(Path root) {
    return p -> {
      try {
        return Files.readString(root.resolve(p));
      } catch (IOException e) {
        return null;
      }
    };
  }
}
