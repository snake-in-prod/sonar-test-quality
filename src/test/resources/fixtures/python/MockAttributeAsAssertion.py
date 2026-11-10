import unittest
from unittest import mock
from unittest.mock import MagicMock

import requests_mock

from app import send


class FakeSender:
    def __init__(self):
        self.sent_once = False


def test_method_not_called():
    m = MagicMock()
    send(m)
    assert m.assert_called_once  # Noncompliant {{call m.assert_called_once() instead}}


def test_nested_method_with_message():
    client = MagicMock()
    send(client)
    assert client.session.post.assert_called_with, "post was not called"  # Noncompliant {{"client.session.post.assert_called_with" is a method that is never called}}


def test_pseudo_attribute():
    m = MagicMock()
    send(m)
    assert m.called_once_with  # Noncompliant {{call m.assert_called_once_with(...) instead}}


def test_in_boolean_expression():
    a = MagicMock()
    b = MagicMock()
    send(a, b)
    assert a.called and (b.assert_not_called)  # Noncompliant {{call b.assert_not_called() instead}}


def test_misspelled_and_not_called():
    m = MagicMock()
    send(m)
    assert m.assert_called_onec  # Noncompliant {{call m.assert_called_once() instead}}


class SendTest(unittest.TestCase):
    @mock.patch("app.smtp")
    def test_assert_true(self, smtp):
        send(None)
        self.assertTrue(smtp.has_calls)  # Noncompliant {{call smtp.assert_has_calls(...) instead}}


# Compliant: real attributes, called assertions, other libraries and fakes


def test_real_attributes():
    m = MagicMock()
    send(m)
    assert m.called
    assert m.call_count == 1
    assert m.call_args
    assert m.method_calls
    assert not m.called


def test_called_assertion():
    m = MagicMock()
    send(m)
    m.assert_called_once()
    assert m.assert_called_once() is None


def test_requests_mock_property():
    with requests_mock.Mocker() as rm:
        rm.get("http://x", text="ok")
        send(None)
        assert rm.called_once


def test_fake_attribute():
    sender = FakeSender()
    send(sender)
    assert sender.sent_once


def test_compared_attribute():
    m = MagicMock()
    send(m)
    assert m.called_with == m.called_with
    assert not m.assert_called


def test_assert_true_with_call():
    m = MagicMock()
    send(m)
    unittest.TestCase().assertTrue(m.called)


def helper(m):
    assert m.assert_called_once
