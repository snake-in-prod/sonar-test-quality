import random
import unittest
from random import choice, randint


def test_inline_in_assert():
    assert f(random.randint(1, 9)) > 0  # Noncompliant {{"random.randint(1, 9)" feeds an assertion}}


def test_assigned_then_asserted():
    n = random.random()  # Noncompliant
    result = scale(n)
    assert result <= 1


def test_imported_function():
    name = choice(NAMES)  # Noncompliant
    user = create(name)
    assert user.name == name


def test_nested_statement():
    for _ in range(3):
        if enabled:
            assert valid(randint(0, 100))  # Noncompliant


class TestUnit(unittest.TestCase):
    def test_self_assert(self):
        items = random.sample(ITEMS, 3)  # Noncompliant
        self.assertEqual(len(dedupe(items)), 3)


# compliant


def test_seeded_instance():
    rng = random.Random(42)
    assert f(rng.randint(1, 9)) > 0


def test_random_not_in_assertion():
    data = random.choice(DATA)
    process(data)
    assert done()


def test_reassigned_before_assert():
    n = random.randint(1, 9)
    n = 5
    assert f(n) > 0


def test_attribute_with_same_name():
    n = random.randint(1, 9)
    assert config.n == 3


def test_random_in_lambda():
    assert retry(lambda: random.random()) is not None


def test_shuffle_not_tracked():
    random.shuffle(items)
    assert sorted(items) == [1, 2, 3]


def test_not_the_random_module(faker):
    assert faker.random.randint(1, 9) > 0


def helper():
    assert f(random.randint(1, 9)) > 0
