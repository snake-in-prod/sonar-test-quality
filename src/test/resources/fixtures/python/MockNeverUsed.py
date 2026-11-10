import unittest
from unittest import mock
from unittest.mock import MagicMock, Mock, patch

import pytest

from app import notify, run


@patch("app.mail")
def test_patched_never_used(mail):  # Noncompliant {{The mock "mail" from @patch("app.mail") is never used}}
    assert run()


@patch("app.mail")
@patch("app.clock")
def test_second_of_two_unused(clock, mail):  # Noncompliant {{"mail"}}
    clock.return_value = 1
    assert run()


def test_created_never_used():
    sender = MagicMock()  # Noncompliant {{The mock "sender" is never used}}
    assert run()


def test_with_never_used():
    with patch.object(notify, "send") as send:  # Noncompliant {{"send"}}
        assert run()


def test_mocker_never_used(mocker):
    m = mocker.patch("app.mail")  # Noncompliant
    assert run()


class NotifyTest(unittest.TestCase):
    @mock.patch("app.mail")
    def test_method_patched_never_used(self, mail):  # Noncompliant
        self.assertTrue(run())

    def test_started_never_used(self):
        smtp = patch("app.smtp").start()  # Noncompliant
        self.addCleanup(patch.stopall)
        self.assertTrue(run())


# Compliant: used mocks, stubs named "_x", patches that pass nothing, ambiguous decorators


@patch("app.mail")
def test_patched_and_asserted(mail):
    run()
    mail.send.assert_called_once()


@patch("app.mail")
def test_patched_stub_named_unused(_mail):
    assert run()


@patch("app.mail", return_value=None)
@patch("app.clock", side_effect=OSError)
def test_configured_stubs(clock, mail):
    assert not run()


def test_configured_stub_bound():
    with patch("app.mail", return_value=None) as mail:
        assert run()


@patch("app.mail")
@patch("app.clock")
def test_names_that_say_unused(unused_mock, ignored):
    assert run()


@patch("app.mail", MagicMock())
@patch("app.clock", new=Mock())
def test_replacement_given(tmp_path):
    assert run(tmp_path)


@pytest.mark.parametrize("x", [1, 2])
@patch("app.mail")
def test_with_parametrize(mail, x):
    run(x)
    assert mail.called


@pytest.fixture
def unknown_decorator():
    return 1


@some_decorator
@patch("app.mail")
def test_other_decorator(mail, value):
    assert run()


def test_created_and_passed():
    sender = Mock()
    assert notify(sender)


def test_with_used():
    with patch("app.mail") as mail, patch("app.clock"):
        run()
    mail.send.assert_called()


def test_with_not_bound():
    with patch("app.mail"):
        assert run()


@patch.dict("os.environ", {"A": "1"})
def test_patch_dict(env):
    assert run()


@patch("app.mail")
def test_star_args(*mocks):
    assert run()


@patch("app.mail")
class PatchedClassTest(unittest.TestCase):
    @patch("app.clock")
    def test_class_patch(self, clock, mail):
        assert run()
