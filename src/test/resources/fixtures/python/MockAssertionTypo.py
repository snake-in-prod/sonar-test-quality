import unittest
from unittest import mock
from unittest.mock import MagicMock, call

from hamcrest import assert_that, equal_to

from app import Mailer, send


class FakeRecorder:
    def __init__(self):
        self.calls = []

    def assert_calls(self, expected):
        assert self.calls == expected


def test_typo_in_assert_name():
    m = MagicMock()
    send(m)
    m.assert_called_onec()  # Noncompliant {{"assert_called_onec" is not a mock assertion; did you mean "assert_called_once"?}}


def test_typo_on_nested_mock():
    client = MagicMock()
    send(client)
    client.session.post.assert_called_once_wiht("/x", data=1)  # Noncompliant {{did you mean "assert_called_once_with"?}}


def test_prefix_misspelled():
    m = MagicMock()
    send(m)
    m.assret_called_with(1)  # Noncompliant {{did you mean "assert_called_with"?}}


def test_assert_prefix_missing():
    m = MagicMock()
    send(m)
    m.called_once_with(1)  # Noncompliant {{call "assert_called_once_with" instead}}


def test_assert_prefix_missing_inside_assert():
    m = MagicMock()
    send(m)
    assert m.has_calls([call(1), call(2)])  # Noncompliant {{call "assert_has_calls" instead}}


def test_made_up_assertion():
    m = MagicMock()
    send(m)
    m.assert_called_twice()  # Noncompliant {{assert m.call_count == 2}}


@mock.patch("app.smtp")
def test_typo_on_patched_mock(smtp):
    send(None)
    smtp.return_value.sendmail.assert_has_call(call("a"))  # Noncompliant {{did you mean "assert_has_calls"?}}


class MailerTest(unittest.TestCase):
    def test_async_typo(self):
        m = mock.AsyncMock()
        Mailer(m).run()
        m.assert_awaited_onec()  # Noncompliant {{did you mean "assert_awaited_once"?}}
        self.mock.not_called()  # Noncompliant


# Compliant: real assertions, real attributes, helpers and fakes that are not mocks


def test_real_assertions():
    m = MagicMock()
    send(m)
    m.assert_called_once_with(1)
    m.assert_has_calls([call(1)])
    m.assert_any_call(1)
    m.assert_not_called()
    m.session.post.assert_awaited_once_with("/x")


def test_real_attributes():
    m = MagicMock()
    send(m)
    assert m.called
    assert m.call_count == 1
    assert m.call_args == call(1)
    assert m.call_args_list == [call(1)]


def test_other_libraries():
    assert_that(send(1), equal_to(2))
    mock.assert_that(1)
    unittest.assert_true(1)


def test_fake_with_own_method():
    recorder = FakeRecorder()
    send(recorder)
    recorder.assert_calls([1])


class HelperTest(unittest.TestCase):
    def assert_called_ok(self, m):
        m.assert_called_once()

    def test_helper_on_self(self):
        m = MagicMock()
        send(m)
        self.assert_called_onec(m)
        self.called_once_with(m)


def test_pseudo_name_not_called():
    m = MagicMock()
    send(m)
    print(m.called_with)
    m.assert_called()


def helper_outside_test(m):
    m.assert_called_onec()
