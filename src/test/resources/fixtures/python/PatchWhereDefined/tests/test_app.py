from unittest import mock
from unittest.mock import patch

from app import notify, run, save


@patch("lib.clock.now")  # Noncompliant {{"lib.clock.now" is patched where it is defined, but app imports it by name and keeps the original; patch "app.now" instead.}}
def test_decorator(now):
    now.return_value = 5
    assert run()


def test_with():
    with patch("lib.clock.now", return_value=5):  # Noncompliant
        assert run()


def test_mocker(mocker):
    send = mocker.patch("lib.mail.send")  # Noncompliant {{patch "app.deliver" instead}}
    notify("a")
    send.assert_called_once()


def test_replacement():
    with mock.patch('lib.clock.now', lambda: 5):  # Noncompliant
        assert run()


# Compliant


@patch("app.now")
def test_patched_where_used(now):
    now.return_value = 5
    assert run()


@patch("lib.store.write")
def test_module_attribute(write):
    save("k")
    write.assert_called_once()


@patch("lib.util.helper")
def test_imported_but_unused(helper):
    assert run()


@patch("lib.clock.Clock.tick")
def test_class_attribute(tick):
    save("k")
    tick.assert_called_once()


@patch("lib.clock.now")
@patch("app.now")
def test_both_patched(app_now, lib_now):
    assert run()


def test_dynamic_target(name):
    with patch(f"lib.clock.{name}"):
        assert run()


def test_local_import():
    with patch("lib.clock.now"):
        from app import run as fresh
        assert fresh()


def test_patch_object():
    with patch.object(mock, "now"):
        assert run()
