import pytest
import sys
import unittest


def test_login():
    assert login("a", "b")


def test_login():  # Noncompliant {{"test_login" is defined again here, so the test on line 6 never runs}}
    assert login("a", "c")


async def test_fetch():
    assert await fetch()


@pytest.mark.slow
async def test_fetch():  # Noncompliant
    assert await fetch(retry=True)


class TestCart:
    def test_add(self):
        assert Cart().add(1)

    def test_add(self):  # Noncompliant
        assert Cart().add(2)

    def test_remove(self):
        assert Cart().remove(1)

    class TestNested:
        def test_empty(self):
            assert Cart().empty()

        def test_empty(self): assert not Cart().items  # Noncompliant


class TestCart:  # Noncompliant {{the class on line 23 never runs}}
    def test_total(self):
        assert Cart().total() == 0


class LoginTest(unittest.TestCase):
    def test_ok(self):
        self.assertTrue(login("a", "b"))

    def test_ok(self):  # Noncompliant
        self.assertTrue(login("a", "c"))


# compliant: same name in different classes
class TestA:
    def test_same(self):
        assert a()


class TestB:
    def test_same(self):
        assert b()


# compliant: alternatives under if / try
if sys.version_info >= (3, 11):
    def test_version():
        assert new_api()
else:
    def test_version():
        assert old_api()

try:
    import fastlib

    def test_speed():
        assert fastlib.run()
except ImportError:
    def test_speed():
        assert slow_run()


# compliant: the first definition is kept before the second one
def test_kept():
    assert kept()


kept_first = test_kept


def test_kept():
    assert kept(again=True)


# compliant: helpers and fixtures, not tests
def helper():
    return 1


def helper():
    return 2


@pytest.fixture
def client():
    return make_client()


# compliant: a property and its setter
class TestProps:
    @property
    def test_value(self):
        return self._v

    @test_value.setter
    def test_value(self, v):
        self._v = v


# compliant: the subclass captures the first class before it is redefined
class TestBase:
    def test_base(self):
        assert base()


class TestChild(TestBase):
    def test_child(self):
        assert child()


class TestBase:
    def test_other(self):
        assert other()


def test_unique_one():
    assert one()


def test_unique_two():
    assert two()
