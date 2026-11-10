package io.github.snakeinprod.testquality.lang.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.snakeinprod.testquality.parse.Nodes;
import org.junit.jupiter.api.Test;

class PyStatementTest {

  private static PyModule module(String src) {
    return new PythonSupport().parse("t.py", "py", src).get(PyModule.class);
  }

  @Test
  void class_with_decorated_methods() {
    PyModule m = module("""
      import pytest

      @pytest.mark.usefixtures("db")
      class TestUser(Base, metaclass=Meta):
          \"""Doc.\"""

          @pytest.mark.parametrize("a, b", [
              (1, 2),
          ])
          @patch("x.y")
          def test_add(self, a, b=2, *args, key: int = 0, **kwargs):
              total = add(a, b)
              assert total == 3

          async def test_async(self, /, x, *, y):
              async with session() as s:
                  await s.get()
      """);
    assertThat(m.statements()).extracting(PyStatement::keyword).containsExactly("import", "class");
    PyStatement cls = m.statements().get(1);
    assertThat(cls.name()).isEqualTo("TestUser");
    assertThat(cls.bases()).containsExactly("Base");
    assertThat(cls.decorators()).hasSize(1);
    assertThat(Nodes.text(cls.decorators().get(0).nodes())).isEqualTo("@ pytest . mark . usefixtures ( \"db\" )");
    assertThat(cls.first().text()).isEqualTo("class");
    assertThat(cls.body()).hasSize(3);

    PyStatement add = cls.body().get(1);
    assertThat(add.keyword()).isEqualTo("def");
    assertThat(add.name()).isEqualTo("test_add");
    assertThat(add.nameToken().line()).isEqualTo(11);
    assertThat(add.isAsync()).isFalse();
    assertThat(add.decorators()).hasSize(2);
    assertThat(add.params()).extracting(PyStatement.Param::name).containsExactly("self", "a", "b", "args", "key", "kwargs");
    assertThat(add.params()).filteredOn(PyStatement.Param::varArgs).extracting(PyStatement.Param::name).containsExactly("args");
    assertThat(add.params()).filteredOn(PyStatement.Param::kwArgs).extracting(PyStatement.Param::name).containsExactly("kwargs");
    assertThat(add.body()).extracting(PyStatement::keyword).containsExactly("total", "assert");
    assertThat(add.last().text()).isEqualTo("3");

    PyStatement async = cls.body().get(2);
    assertThat(async.keyword()).isEqualTo("def");
    assertThat(async.isAsync()).isTrue();
    assertThat(async.name()).isEqualTo("test_async");
    assertThat(async.params()).extracting(PyStatement.Param::name).containsExactly("self", "x", "y");
    assertThat(async.body().get(0).keyword()).isEqualTo("with");
    assertThat(async.body().get(0).isAsync()).isTrue();
    assertThat(async.body().get(0).body()).hasSize(1);

    assertThat(cls.walk()).hasSize(8);
    assertThat(m.walk()).hasSize(9);
  }

  @Test
  void one_line_suites_and_compound_chains() {
    PyModule m = module("""
      def f(x) -> int: return x
      if a: b = 1
      elif c: pass
      else:
          d = {1: 2}
      try:
          g()
      except (A, B) as e:
          raise
      finally: h()
      x: int = 1
      f = lambda: 0
      """);
    assertThat(m.statements()).extracting(PyStatement::keyword)
      .containsExactly("def", "if", "elif", "else", "try", "except", "finally", "x", "f");
    PyStatement def = m.statements().get(0);
    assertThat(Nodes.text(def.nodes())).isEqualTo("def f ( x ) -> int :");
    assertThat(def.body()).hasSize(1);
    assertThat(def.body().get(0).keyword()).isEqualTo("return");
    assertThat(m.statements().get(1).body().get(0).keyword()).isEqualTo("b");
    assertThat(m.statements().get(3).body().get(0).nodes()).hasSize(3);
    assertThat(m.statements().get(5).body()).extracting(PyStatement::keyword).containsExactly("raise");
    assertThat(m.statements().get(7).body()).isEmpty();
    assertThat(m.statements().get(8).body()).isEmpty();
  }

  @Test
  void imports_map_local_names() {
    PyModule m = module("""
      import os
      import a.b as c, json
      import x.y.z
      from a.b import c2 as d, e
      from unittest import (
          mock,
          TestCase as TC,
      )
      from . import rel
      from ..pkg.mod import thing
      from star import *
      try:
          import simplejson as json2
      except ImportError:
          json2 = None
      """);
    assertThat(m.imports()).contains(entry("os", "os"), entry("c", "a.b"), entry("json", "json"), entry("x", "x"),
      entry("x.y.z", "x.y.z"), entry("d", "a.b.c2"), entry("e", "a.b.e"), entry("mock", "unittest.mock"),
      entry("TC", "unittest.TestCase"), entry("rel", ".rel"), entry("thing", "..pkg.mod.thing"), entry("json2", "simplejson"));
    assertThat(m.imports()).doesNotContainKey("*");
    assertThat(m.resolve("mock.patch.object")).isEqualTo("unittest.mock.patch.object");
    assertThat(m.resolve("TC")).isEqualTo("unittest.TestCase");
    assertThat(m.resolve("x.y.z.f")).isEqualTo("x.y.z.f");
    assertThat(m.resolve("unknown.name")).isEqualTo("unknown.name");
  }

  @Test
  void stray_decorators_and_indents_do_not_break() {
    PyModule m = module("@deco\nx = 1\n    y = 2\nz = 3\n@dangling\n");
    assertThat(m.walk()).extracting(s -> Nodes.text(s.nodes()))
      .containsExactly("@ deco", "x = 1", "y = 2", "z = 3", "@ dangling");
    assertThat(module("").statements()).isEmpty();
    assertThat(module("def f(:\n").statements()).hasSize(1);
    assertThat(module("class A(:\n    pass\n").statements()).extracting(PyStatement::name).containsExactly("A");
    assertThat(module("if x:\n").statements().get(0).body()).isEmpty();
  }
}
