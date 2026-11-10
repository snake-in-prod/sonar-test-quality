package io.github.snakeinprod.testquality.lang.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.snakeinprod.testquality.model.TestCase;
import io.github.snakeinprod.testquality.model.TestFile;
import io.github.snakeinprod.testquality.parse.ParsedFile;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PythonSupportTest {

  private final PythonSupport support = new PythonSupport();

  private static String fixture(String name) throws IOException {
    return Files.readString(Path.of("src/test/resources/model/python", name));
  }

  private TestFile extract(String source) {
    return support.extract(support.parse("test_x.py", "py", source));
  }

  private static TestCase find(TestFile f, String name) {
    return f.tests().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void parse_attaches_the_module() {
    ParsedFile file = support.parse("a/test_x.py", "py", "def test_a():\n    assert 1\n");
    assertThat(file.language()).isEqualTo("py");
    assertThat(file.get(PyModule.class).statements()).hasSize(1);
    assertThat(file.root().children()).anyMatch(n -> n.first().kind() == TokenKind.INDENT);
  }

  @Test
  void unittest_classes() throws IOException {
    TestFile f = extract(fixture("unittest_sample.py"));
    assertThat(f.tests()).extracting(TestCase::suite, TestCase::name, TestCase::skipped, TestCase::async, TestCase::assertions,
      TestCase::mocks).containsExactly(
        tuple("UserTests", "test_create", false, false, 2, 0),
        tuple("UserTests", "test_send", false, false, 2, 2),
        tuple("UserTests", "test_skip_at_start", true, false, 1, 0),
        tuple("UserTests", "test_skipped", true, false, 1, 0),
        tuple("UserTests", "test_async", false, true, 1, 0),
        tuple("SkippedSuite", "test_one", true, false, 1, 1));
    TestCase send = find(f, "test_send");
    assertThat(send.nameToken().text()).isEqualTo("test_send");
    assertThat(send.start().text()).isEqualTo("@");
    assertThat(send.end().text()).isEqualTo(")");
    assertThat(send.end().line()).isEqualTo(28);
    assertThat(send.body()).isInstanceOf(PyStatement.class);
    assertThat(((PyStatement) send.body()).name()).isEqualTo("test_send");
  }

  @Test
  void pytest_sample() throws IOException {
    TestFile f = extract(fixture("pytest_sample.py"));
    assertThat(f.tests()).hasSize(43);
    assertThat(f.tests()).extracting(TestCase::name)
      .doesNotContain("helper", "test_never_collected", "test_never_collected_either", "make_order", "service")
      .contains("test_total", "test_last_one", "test_inner", "test_unicode_identifiers");
    assertThat(f.tests()).filteredOn(TestCase::skipped).extracting(TestCase::name)
      .containsExactly("test_skipped_by_mark", "test_xfail_via_imported_mark", "test_skipif", "test_skip_at_start", "test_a", "test_b");
    assertThat(f.tests()).filteredOn(TestCase::async).extracting(TestCase::name).containsExactly("test_async_place", "test_async_method");
    assertThat(f.tests()).filteredOn(t -> !t.skipped() && t.assertions() == 0).extracting(TestCase::name).containsExactly("test_without_assertion");

    assertThat(find(f, "test_inner").suite()).isEqualTo("TestOrderStatus > TestNested");
    assertThat(find(f, "test_new").suite()).isEqualTo("TestOrderStatus");
    assertThat(find(f, "test_total").suite()).isEmpty();

    assertThat(f.tests()).filteredOn(t -> t.mocks() > 0).extracting(TestCase::name, TestCase::mocks, TestCase::assertions).containsExactly(
      tuple("test_mail_sent", 1, 1),
      tuple("test_mocker", 2, 2),
      tuple("test_monkeypatch", 1, 1),
      tuple("test_async_place", 1, 1),
      tuple("test_one", 1, 1),
      tuple("test_two", 2, 1));
    assertThat(f.tests()).extracting(TestCase::name, TestCase::assertions).contains(
      tuple("test_raises", 1), tuple("test_raises_imported", 1), tuple("test_warns", 1), tuple("test_cancel", 2),
      tuple("test_one_line_suite", 2), tuple("test_try_except", 1), tuple("test_nested_function_assertions", 1),
      tuple("test_decimal_math", 5), tuple("test_comment_only_lines", 1), tuple("test_match_statement", 1),
      tuple("test_total_with_continuation", 1), tuple("test_nested_fstrings", 2), tuple("test_fstring_same_quotes_312", 1));
  }

  @Test
  void imported_test_case_bases_and_module_skip() {
    TestFile f = extract("""
      import unittest as ut
      from django.test import TestCase as DjangoCase
      from pytest import mark
      pytestmark = mark.skipif(True, reason="off")

      class A(ut.TestCase):
          def test_a(self):
              self.assertIn(1, [1])

      class B(DjangoCase):
          def test_b(self):
              pass

      class C(ut.IsolatedAsyncioTestCase):
          async def asyncSetUp(self):
              pass

          async def test_c(self):
              pass
      """);
    assertThat(f.tests()).extracting(TestCase::suite, TestCase::name, TestCase::skipped, TestCase::async).containsExactly(
      tuple("A", "test_a", true, false), tuple("B", "test_b", true, false), tuple("C", "test_c", true, true));
  }

  @Test
  void fixtures_named_test_are_not_tests() {
    TestFile f = extract("""
      from contextlib import contextmanager
      import pytest
      from pytest import fixture

      @pytest.fixture
      def test_apps(monkeypatch):
          monkeypatch.syspath_prepend("apps")

      @fixture(scope="module")
      def test_client():
          return 1

      def test_real(test_client):
          assert test_client

      @contextmanager
      def test_server_running():
          yield
      """);
    assertThat(f.tests()).extracting(TestCase::name).containsExactly("test_real");
  }

  @Test
  void no_tests_gives_empty() {
    assertThat(extract("def helper():\n    return 1\n")).isSameAs(TestFile.EMPTY);
    assertThat(extract("")).isSameAs(TestFile.EMPTY);
  }

  @Test
  void a_large_file_parses_fast() throws IOException {
    String source = fixture("pytest_sample.py").repeat(20);
    assertThat(source.lines().count()).isGreaterThan(6000);
    extract(source); // warm up
    long start = System.nanoTime();
    TestFile f = extract(source);
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(f.tests()).hasSize(43 * 20);
    assertThat(millis).as("parse and extract of %d lines", source.lines().count()).isLessThan(2000);
  }
}
