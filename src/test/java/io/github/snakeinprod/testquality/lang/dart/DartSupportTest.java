package io.github.snakeinprod.testquality.lang.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.Chain;
import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Nodes;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DartSupportTest {

  private final DartSupport support = new DartSupport();

  private TestFile extract(String fixture) throws IOException {
    String src = Files.readString(Path.of("src/test/resources/model/dart", fixture));
    return support.extract(support.parse(fixture, "dart", src));
  }

  private ParsedFile parse(String src) {
    return support.parse("a_test.dart", "dart", src);
  }

  @Test
  void widget_tests_goldens_and_groups() throws IOException {
    TestFile f = extract("widget_test.dart");
    assertThat(f.tests()).extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::async, TestCase::assertions,
      TestCase::mocks).containsExactly(
        tuple("counter increments", "", false, true, 2, 0),
        tuple("matches golden", "golden", true, true, 1, 0),
        tuple("all sizes", "golden", false, true, 1, 0));
    TestCase first = f.tests().get(0);
    assertThat(first.nameToken().text()).isEqualTo("'counter increments'");
    assertThat(first.start().text()).isEqualTo("testWidgets");
    assertThat(first.start().line()).isEqualTo(8);
    assertThat(first.end().line()).isEqualTo(14);
    assertThat(first.bodyGroup().is("{")).isTrue();
  }

  @Test
  void package_test_units_with_nested_groups_and_skip() throws IOException {
    TestFile f = extract("unit_test.dart");
    assertThat(f.tests()).extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::async, TestCase::assertions)
      .containsExactly(
        tuple("adds", "", false, false, 1),
        tuple("arrow body", "", false, false, 1),
        tuple("joined description", "", false, true, 1),
        tuple("underscores", "parser > numbers", false, false, 2),
        tuple("pending", "parser > numbers", true, false, 0),
        tuple("conditional skip is not a skip", "parser", false, false, 1),
        tuple("inherits skip", "skipped group", true, false, 1));
    assertThat(f.tests().get(1).bodyGroup().open()).isNull();
    assertThat(f.tests().get(2).nameToken().text()).isEqualTo("'joined '");
    assertThat(f.skipped()).isEqualTo(2);
    assertThat(f.withoutAssertion()).isZero();
  }

  @Test
  void mockito_mocks_and_fakes() throws IOException {
    TestFile f = extract("mockito_test.dart");
    assertThat(f.tests()).extracting(TestCase::name, TestCase::assertions, TestCase::mocks).containsExactly(
      tuple("loads from the repo", 3, 2),
      tuple("uses a fake", 1, 1),
      tuple("never called", 1, 1));
  }

  @Test
  void mocktail_mocks_declared_in_the_file() throws IOException {
    TestFile f = extract("mocktail_test.dart");
    assertThat(f.tests()).extracting(TestCase::name, TestCase::async, TestCase::assertions, TestCase::mocks).containsExactly(
      tuple("fetches", true, 2, 2),
      tuple("no checks", true, 0, 1));
    assertThat(f.mockHeavy()).isEqualTo(1);
  }

  @Test
  void bloc_tests_count_expect_errors_and_verify() throws IOException {
    TestFile f = extract("bloc_test.dart");
    assertThat(f.tests()).extracting(TestCase::name, TestCase::suite, TestCase::skipped, TestCase::assertions, TestCase::mocks)
      .containsExactly(
        tuple("emits [] when nothing is added", "CounterBloc", false, 1, 0),
        tuple("skips the seed", "CounterBloc", false, 4, 2),
        tuple("only acts", "CounterBloc", false, 0, 0),
        tuple("inherits skip", "CounterBloc > skipped", true, 1, 0));
    TestCase seed = f.tests().get(1);
    assertThat(seed.start().text()).isEqualTo("blocTest");
    assertThat(seed.nameToken().text()).isEqualTo("'skips the seed'");
    assertThat(seed.bodyGroup().is("(")).isTrue();
    assertThat(seed.end()).isSameAs(seed.bodyGroup().last());
  }

  @Test
  void no_tests_gives_empty() {
    assertThat(support.extract(parse("void main() { print('test'); }"))).isSameAs(TestFile.EMPTY);
    assertThat(support.extract(parse("void test(String d, Function f) {}\nfoo.test('x', () {});"))).isSameAs(TestFile.EMPTY);
  }

  @Test
  void closures_named_args_and_statements() {
    ParsedFile p = parse("f(() async => x, (a) { b; }, n: 1, m: 'x');");
    Node.Group args = (Node.Group) p.root().children().get(1);
    List<List<Node>> pos = DartTrees.positionalArgs(args);
    DartTrees.Closure arrow = DartTrees.closure(pos.get(0));
    assertThat(arrow.async()).isTrue();
    assertThat(arrow.arrow()).isTrue();
    assertThat(Nodes.text(arrow.body())).isEqualTo("x");
    DartTrees.Closure block = DartTrees.closure(pos.get(1));
    assertThat(block.async()).isFalse();
    assertThat(block.body().is("{")).isTrue();
    Map<String, List<Node>> named = DartTrees.namedArgs(args);
    assertThat(named.keySet()).containsExactly("n", "m");
    assertThat(Nodes.text(named.get("m"))).isEqualTo("'x'");
    assertThat(DartTrees.closure(List.of(args))).isNull();

    Node.Group body = (Node.Group) parse("{ a(); if (x) { b(); } else { c(); } final m = {1: 2}; try { d(); } on E catch (e) {} e() }")
      .root().children().get(0);
    assertThat(DartTrees.statements(body)).extracting(Nodes::text).containsExactly("a ( )", "if ( x ) { b ( ) ; } else { c ( ) ; }",
      "final m = { 1 : 2 }", "try { d ( ) ; } on E catch ( e ) { }", "e ( )");
  }

  @Test
  void awaited_and_returned_chains() {
    ParsedFile p = parse("t() async { await expectLater(a, b); expectLater(c, d); return expectLater(e, f); }\nu() => expectLater(g, h);");
    List<Chain> chains = p.chains().stream().filter(c -> c.firstName().equals("expectLater")).toList();
    assertThat(chains).extracting(DartTrees::isAwaited).containsExactly(true, false, false, false);
    assertThat(chains).extracting(DartTrees::isReturned).containsExactly(false, false, true, true);
  }

  @Test
  void string_value_strips_prefix_and_quotes() {
    assertThat(DartTrees.stringValue(parse("r'a$b'").tokens().get(0))).isEqualTo("a$b");
    assertThat(DartTrees.stringValue(parse("'''x'''").tokens().get(0))).isEqualTo("x");
    assertThat(DartTrees.stringValue(parse("''").tokens().get(0))).isEmpty();
    assertThat(DartTrees.stringValue(parse("'abc").tokens().get(0))).isEqualTo("abc");
  }
}
