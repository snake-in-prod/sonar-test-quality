import unittest

import pytest
from pytest import raises


def test_any_exception():
    with pytest.raises(Exception):  # Noncompliant {{"pytest.raises(Exception)" passes on any error}}
        parse("x")


def test_base_exception_call_form():
    pytest.raises(BaseException, parse, "x")  # Noncompliant


def test_setup_inside_block():
    with pytest.raises(ValueError):  # Noncompliant {{wraps 2 statements}}
        order = make_order()
        order.submit()


def test_imported_raises_three_calls(client):
    with raises(KeyError):  # Noncompliant {{wraps 3 statements}}
        client.login()
        client.load("cart")
        client.checkout()


class TestApi(unittest.TestCase):
    def test_unittest_broad(self):
        with self.assertRaises(Exception):  # Noncompliant
            api.call()

    def test_unittest_setup_inside(self):
        with self.assertRaises(ValueError):  # Noncompliant
            user = create_user()
            user.rename("")


# compliant


def test_specific_one_call():
    with pytest.raises(ValueError):
        parse("x")


def test_broad_with_match():
    with pytest.raises(Exception, match="invalid id"):
        parse("x")


def test_broad_alias_checked():
    with pytest.raises(Exception) as exc:
        parse("x")
    assert exc.type is ValueError


def test_setup_with_alias_checked():
    with pytest.raises(ValueError) as exc:
        order = make_order()
        order.submit()
    assert "empty" in str(exc.value)


def test_setup_outside_block():
    order = make_order()
    with pytest.raises(ValueError):
        order.submit()


def test_value_building_inside():
    with pytest.raises(ValueError):
        data = str(42)
        parse(data)


def test_assignment_without_call():
    with pytest.raises(ValueError):
        value = "x"
        parse(value)


def test_assertion_inside_is_another_rule():
    with pytest.raises(ValueError):
        parse("x")
        assert not seen


def test_compound_body():
    with pytest.raises(ValueError):
        for value in values:
            parse(value)
        cleanup()


def test_custom_exception_class():
    with pytest.raises(errors.Exception):
        parse("x")


def test_tuple_of_specific():
    with pytest.raises((ValueError, TypeError)):
        parse("x")


class TestRegex(unittest.TestCase):
    def test_regex_variant(self):
        with self.assertRaisesRegex(Exception, "bad"):
            parse("x")


def helper():
    with pytest.raises(Exception):
        parse("x")
