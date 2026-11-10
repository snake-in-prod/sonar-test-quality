import pytest
import unittest


def test_login():
    assert login("a", "b")


def check_login():  # Noncompliant {{"check_login" has assertions but is never run}}
    assert login("a", "c")


def Test_logout():  # Noncompliant
    assert logout()


async def tset_fetch():  # Noncompliant
    with pytest.raises(TimeoutError):
        await fetch(timeout=0)


def refresh_test():  # Noncompliant
    assert refresh() is not None


class CartTests:  # Noncompliant {{pytest never collects "CartTests"}}
    def test_add(self):
        assert Cart().add(1)


class TestOrders:
    def __init__(self):  # Noncompliant {{because it defines __init__}}
        self.orders = []

    def test_empty(self):
        assert not self.orders


class TestPayments:
    def test_pay(self):
        assert pay(1)

    def verify_refund(self):  # Noncompliant
        assert refund(1)


class AccountCase(unittest.TestCase):
    def tst_balance(self):  # Noncompliant
        self.assertEqual(balance(), 0)

    def test_open(self):
        self.assertTrue(open_account())


# compliant: helpers that take arguments
def check_response(response, status=200):
    assert response.status == status


# compliant: helpers called by the tests
def ensure_clean():
    assert not leftovers()


def test_clean():
    ensure_clean()
    assert done()


# compliant: deliberately disabled, framework hooks, fixtures
def _test_disabled():
    assert broken()


def setup_module():
    assert prepare()


def pytest_configure(config):
    assert config


@pytest.fixture
def ready_db():
    db = connect()
    assert db.ping()
    return db


# compliant: no assertions
def build_user():
    return User("a")


# compliant: names found in strings (getattr, __all__)
def verify_schema():
    assert schema_ok()


__all__ = ["verify_schema"]


# compliant: classes that are mixins, bases, used elsewhere or unittest cases
class CheckoutMixin:
    def test_checkout(self):
        assert self.checkout()


class SharedCases:
    def test_shared(self):
        assert self.run()


class TestShared(SharedCases):
    def run(self):
        return True


class StoreSuite(unittest.TestCase):
    def test_store(self):
        self.assertTrue(store())


class Inventory(Base):
    def test_count(self):
        assert self.count() == 0


class TestWithSetup:
    def setup_method(self):
        assert reset()

    def helper(self, value):
        assert value

    def test_value(self):
        self.helper(1)


class TestRunner(unittest.TestCase):
    def setUp(self):
        self.assertTrue(start())

    def runTest(self):
        self.assertTrue(run_all())


class TestData:
    def __init__(self, rows):
        self.rows = rows

    def count(self):
        return len(self.rows)
