import unittest
from unittest import mock

import pytest
from pytest import raises


def test_message_checked_inside():
    with pytest.raises(ValueError) as exc:
        parse("x")
        assert "bad" in str(exc.value)  # Noncompliant {{"parse("x")" raises first}}


def test_two_assertions_inside():
    with pytest.raises(KeyError, match="id"):
        result = lookup({}, "id")
        assert result is None  # Noncompliant
        assert calls == 1  # Noncompliant


def test_imported_raises():
    with raises(TypeError):
        add(1, "2")
        sender.assert_not_called()  # Noncompliant {{"with raises(...)"}}


async def test_async_client(client):
    async with client:
        with pytest.raises(ConnectionError):
            await client.get("/down")
            assert client.retries == 3  # Noncompliant


def test_inside_loop():
    for value in ["", None]:
        with pytest.raises(ValueError):
            parse(value)
            assert value  # Noncompliant


class TestParser(unittest.TestCase):
    def test_unittest_context(self):
        with self.assertRaises(ValueError) as cm:
            parse("")
            self.assertEqual(str(cm.exception), "empty")  # Noncompliant


# compliant


def test_assertion_after_block():
    with pytest.raises(ValueError) as exc:
        parse("x")
    assert "bad" in str(exc.value)


def test_assertion_is_the_raising_call():
    with pytest.raises(ValueError):
        assert parse("x") == 1


def test_setup_assertion_before_raising_call():
    with pytest.raises(ValueError):
        obj = make()
        assert obj.ready
        obj.parse("x")


def test_should_have_raised_guard():
    with pytest.raises(ValueError):
        parse("x")
        assert False, "parse should raise"


def test_expects_assertion_error():
    with pytest.raises(AssertionError):
        sender.send()
        sender.assert_called_once()


def test_warns_keeps_running():
    with pytest.warns(DeprecationWarning):
        old_api()
        assert done


def test_no_call_before():
    with pytest.raises(ValueError):
        value = 3
        assert value


def test_compound_before():
    with pytest.raises(ValueError):
        if flag:
            parse("x")
        assert flag


@pytest.mark.parametrize("exception", [None, ValueError])
def test_raises_only_in_some_cases(runner, exception):
    with (
        pytest.raises(exception, match="bad")
        if exception
        else nullcontext()
    ):
        result = runner.invoke(cmd)
        assert "bad" in result.stderr


def test_other_context_manager():
    with mock.patch("app.send"):
        run()
        assert sent


class TestFail(unittest.TestCase):
    def test_fail_guard(self):
        with self.assertRaises(ValueError):
            parse("")
            self.fail("should raise")


def helper():
    with pytest.raises(ValueError):
        parse("x")
        assert seen
