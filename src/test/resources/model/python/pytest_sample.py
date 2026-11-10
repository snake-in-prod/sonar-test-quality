"""Tests for the order service.

A docstring with 'quotes', "double quotes" and a # that is not a comment.
"""
from __future__ import annotations

import asyncio
import json
import os.path as osp
from dataclasses import dataclass, field
from decimal import Decimal
from unittest.mock import MagicMock, AsyncMock, patch

import pytest
from pytest import mark, raises

from shop.orders import (
    Order,
    OrderLine,
    OrderService,  # the service under test
)
from . import factories
from .helpers import make_user as user_factory

pytestmark = pytest.mark.django_db

TAX = Decimal("0.23")
PRICES = {
    "apple": 1_000,
    "pear": 0x1F,
    "plum": 1e-3,
    "fig": 3j,
}
LONG_TEXT = '''single "double" and 'single' quotes
# not a comment
and a closing ''' + r"raw \d+ string" + b"bytes" + Rb'raw bytes'


@dataclass
class Basket:
    lines: list = field(default_factory=list)

    def total(self) -> Decimal:
        return sum((l.price * l.qty for l in self.lines), Decimal(0))


@pytest.fixture
def service():
    repo = MagicMock()
    return OrderService(repo=repo, clock=lambda: 0)


@pytest.fixture
def basket():
    return Basket(lines=[
        OrderLine("apple", 2, Decimal("1.00")),
        OrderLine("pear", 1, Decimal("2.50")),
    ])


def make_order(**kwargs):
    defaults = dict(id=1, lines=[], status="new")
    defaults.update(kwargs)
    return Order(**defaults)


def test_total(basket):
    assert basket.total() == Decimal("4.50")


def test_total_with_continuation(basket):
    expected = Decimal("1.00") * 2 + \
        Decimal("2.50")
    assert basket.total() == expected, \
        f"total was {basket.total()!r}, expected {expected:>10}"


def test_nested_fstrings():
    name = "x"
    text = f"{f'{name!r}'} and {', '.join([f'{n}' for n in 'ab'])}"
    other = f"""{name} "quoted" {{literal}} {name:{'>'}{10}} # not a comment"""
    assert text
    assert other


def test_fstring_same_quotes_312():
    items = {"a": 1}
    assert f"{items["a"]}" == "1"


@pytest.mark.parametrize(
    "qty, expected",
    [
        (1, Decimal("1.00")),
        (2, Decimal("2.00")),
        (3, Decimal("3.00")),
    ],
)
def test_line_total(qty, expected):
    line = OrderLine("apple", qty, Decimal("1.00"))
    assert line.total() == expected


@pytest.mark.skip(reason="flaky on CI")
def test_skipped_by_mark():
    assert False


@mark.xfail(strict=True)
def test_xfail_via_imported_mark():
    assert 1 == 2


@pytest.mark.skipif(os.name == "nt", reason="posix only")
def test_skipif():
    assert osp.sep == "/"


def test_skip_at_start():
    pytest.skip("not implemented")
    assert True


def test_raises():
    with pytest.raises(ValueError, match="bad"):
        OrderService(repo=None).place(None)


def test_raises_imported():
    with raises(KeyError):
        {}["missing"]


def test_warns():
    with pytest.warns(DeprecationWarning):
        OrderService.legacy()


@patch("shop.orders.mailer")
def test_mail_sent(mailer, service):
    service.place(make_order())
    mailer.send.assert_called_once()


def test_mocker(mocker, service):
    clock = mocker.patch("shop.orders.now", return_value=0)
    spy = mocker.patch.object(service, "notify")
    service.place(make_order())
    clock.assert_called()
    spy.assert_called_once_with(1)


def test_monkeypatch(monkeypatch, service):
    monkeypatch.setattr("shop.orders.TAX", Decimal("0"))
    assert service.tax(make_order()) == 0


def test_without_assertion(service):
    service.place(make_order())


async def test_async_place(service):
    service.repo.save = AsyncMock()
    await service.place_async(make_order())
    service.repo.save.assert_awaited_once()


class TestOrderStatus:
    """pytest class: collected, no __init__."""

    pytestmark = [pytest.mark.slow]

    def test_new(self):
        assert make_order().status == "new"

    def test_cancel(self, service):
        order = make_order()
        service.cancel(order)
        assert order.status == "cancelled"
        assert order.cancelled_at is not None

    def helper(self):
        assert False

    @staticmethod
    def test_static():
        assert True

    async def test_async_method(self, service):
        result = await service.status_async(1)
        assert result in {"new", "paid"}

    class TestNested:
        def test_inner(self):
            assert [x for x in range(3) if x % 2] == [1]


class TestSkippedClass:
    pytestmark = pytest.mark.skip(reason="whole class")

    def test_a(self):
        assert True

    def test_b(self):
        assert True


class TestWithInit:
    def __init__(self):
        self.x = 1

    def test_never_collected(self):
        assert self.x


class NotATest:
    def test_never_collected_either(self):
        assert True


@patch("shop.orders.repo")
class TestPatchedClass:
    def test_one(self, repo):
        assert repo

    def test_two(self, repo):
        m = MagicMock()
        m.close()
        assert m.close.called


def test_one_line_suite(service):
    if service: assert service.ready()
    for i in range(2): assert i < 2


def test_dict_and_slices():
    data = {"a": [1, 2, 3][0:2], "b": {"c": (1,)}}
    assert data["a"][::-1] == [2, 1]
    assert data["b"]["c"][0] == 1


def test_walrus_and_lambda():
    if (n := len("abc")) > 2:
        assert n == 3
    key = lambda x: x[1]
    assert sorted([(1, 2), (0, 1)], key=key)[0] == (0, 1)


def test_try_except():
    try:
        json.loads("{")
    except json.JSONDecodeError as exc:
        assert "Expecting" in str(exc)
    else:
        pytest.fail("no error")
    finally:
        pass


def test_with_multiple_context_managers(tmp_path):
    with open(tmp_path / "a.txt", "w") as fh, \
            open(tmp_path / "b.txt", "w") as gh:
        fh.write("a")
        gh.write("b")
    assert (tmp_path / "a.txt").read_text() == "a"


def test_match_statement():
    command = ("go", "north")
    match command:
        case ("go", direction):
            assert direction == "north"
        case _:
            pytest.fail("no match")


def test_decimal_math():
    assert Decimal("0.1") + Decimal("0.2") == Decimal("0.3")
    assert round(1.005, 2) <= 1.01
    assert 10 // 3 == 3
    assert 2 ** 10 == 1024
    assert -7 % 3 == 2


def test_string_prefixes():
    values = [u"u", U"U", r"\n", R"\n", b"b", B"b", br"\x", rB"\x", f"{1}", F"{2}", fr"{3}\d", Rf"{4}"]
    assert len(values) == 12


def test_comment_only_lines():
    # a comment at the body indentation
        # a comment deeper than the body
# a comment at column zero
    value = 1

    # blank line above
    assert value == 1


def test_tabs_and_spaces():
	assert True


def test_line_with_semicolons():
    a = 1; b = 2
    assert a + b == 3


def test_ellipsis_and_annotations() -> None:
    x: int = 1
    y: tuple[int, ...] = (1, 2)
    assert x == y[0]


def test_matrix_operator():
    class M:
        def __matmul__(self, other):
            return 42
    assert M() @ M() == 42


def test_nested_function_assertions():
    def check(value):
        assert value > 0
    check(1)


@pytest.mark.parametrize("value", [1, 2, 3])
@pytest.mark.parametrize("factor", [10, 100])
def test_stacked_parametrize(value, factor):
    assert value * factor >= 10


def test_unicode_identifiers():
    café = "coffee"
    assert café == "coffee"


def test_last_one(service):
    """Final test; the file ends without a trailing blank line."""
    orders = [make_order(id=i) for i in range(10)]
    for order in orders:
        service.place(order)
    assert service.repo.save.call_count == 10
