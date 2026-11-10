import unittest


def test_sum():
    result = add(1, 2)
    result == 3  # Noncompliant {{The result of "result == 3" is dropped}}


def test_membership():
    users = load()
    "alice" in users  # Noncompliant
    users[0].name != ""  # Noncompliant


def test_identity():
    value = get()
    value is None  # Noncompliant
    value is not other  # Noncompliant
    not value in cache  # Noncompliant


def test_nested_blocks():
    for item in items():
        if item.ready:
            item.size > 0  # Noncompliant
    with open_db() as db:
        db.count() <= 10  # Noncompliant


async def test_async():
    await fetch() == 200  # Noncompliant


def test_chained():
    0 < ratio() < 1  # Noncompliant


class TestCompare(unittest.TestCase):
    def test_eq(self):
        self.x = compute()
        self.x == 4  # Noncompliant


# compliant
def test_asserted():
    result = add(1, 2)
    assert result == 3
    assert "alice" in load()


def test_assigned():
    same = add(1, 2) == 3
    flags = [x == 1 for x in values()]
    ok: bool = same == True
    same &= other == 1
    assert same and flags and ok


def test_short_circuit_side_effects():
    result = add(1, 2)
    result == 3 or pytest.fail("bad sum")
    result != 3 and report(result)
    notify() if result == 3 else retry()


def test_calls_and_compound():
    print(add(1, 2) == 3)
    check(result == 3)
    if add(1, 2) == 3:
        log("ok")
    while queue.size() > 0:
        queue.pop()
    for key in mapping:
        use(key)
    self_check = lambda: total() == 3
    assert self_check()


def test_returns_and_yields():
    gen = (x for x in data() if x > 0)
    assert list(gen)


def test_other_operators():
    flags << 2
    mask >> 1
    lst[0:1]
    assert mask


def helper(result):
    result == 3
