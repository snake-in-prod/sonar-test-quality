import unittest


def test_sum():
    return add(1, 2) == 3  # Noncompliant {{The test returns "add(1, 2) == 3" instead of asserting it}}


def test_in():
    users = load()
    return "alice" in users  # Noncompliant


def test_branch():
    value = get()
    if value:
        return value is not None  # Noncompliant
    return value == default() and ready()  # Noncompliant


async def test_async():
    return await fetch() != 500  # Noncompliant


def test_one_line(): return compute() >= 0  # Noncompliant


class TestThings(unittest.TestCase):
    def test_eq(self):
        return self.thing() == 4  # Noncompliant


# compliant
def test_asserted():
    assert add(1, 2) == 3


def test_early_return():
    if not enabled():
        return
    assert run()


def test_returns_non_comparison():
    assert run()
    return None


def test_nested_helper():
    def matches(x):
        return x == 3

    class Spec:
        def check(self, y):
            return y > 0

    assert matches(add(1, 2))
    assert Spec().check(1)


def test_lambda_and_conditional():
    key = lambda x: x == 1
    assert key(1)
    return first() if mode() == "a" else second()


def test_reused():
    return total() == 3


def test_uses_reused():
    assert test_reused()


def helper():
    return add(1, 2) == 3


def test_returns_call():
    return check(add(1, 2) == 3)
