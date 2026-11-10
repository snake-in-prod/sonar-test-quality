import pytest
from pytest import mark, param


@pytest.mark.parametrize("a,b", [(1, 2), (2,), (3, 4)])  # Noncompliant {{This row does not have 2 values for "a,b"}}
def test_pairs(a, b):
    assert a < b


@pytest.mark.parametrize(
    "value, expected",
    [
        (1, 2),
        (2, 3, 4),  # Noncompliant
        pytest.param(5, id="five"),  # Noncompliant
    ],
)
def test_rows(value, expected):
    assert value + 1 == expected


@mark.parametrize("x, y", [(1, 2, 3), (4, 5, 6)])  # Noncompliant {{"x, y" names 2 parameters but every row has 3 values}}
def test_every_row(x, y):
    assert x < y


@pytest.mark.parametrize("expected", [1, 2])  # Noncompliant {{did you mean "expect"?}}
def test_typo(expect):
    assert expect


@pytest.mark.parametrize(("name", "size"), [("a", 1), ("b", 2)])  # Noncompliant {{"test_no_params" has no parameter "name"}}
def test_no_params():
    assert True


class TestInClass:
    @pytest.mark.parametrize("item", [param(1, 2), param(3)])  # Noncompliant
    def test_param_rows(self, item):
        assert item


# compliant
@pytest.mark.parametrize("a,b", [(1, 2), (2, 3)])
def test_ok(a, b):
    assert a < b


@pytest.mark.parametrize("value", [(1, 2), (3,), [4, 5, 6]])
def test_single_name_tuples(value):
    assert value


@pytest.mark.parametrize("a, b,", [(1, 2), pytest.param(3, 4, marks=pytest.mark.slow)])
def test_trailing_comma_and_param(a, b):
    assert a < b


@pytest.mark.parametrize("a,b", CASES)
@pytest.mark.parametrize("c,d", [row for row in load()])
@pytest.mark.parametrize("e,f", [*EXTRA, (1, 2)])
@pytest.mark.parametrize("g,h", [(x), make_row(), (1, 2)])
def test_dynamic_rows(a, b, c, d, e, f, g, h):
    assert a


@pytest.mark.parametrize("db_url", ["sqlite://"])
def test_fixture_dependency(db):
    assert db.ping()


@pytest.mark.parametrize("server", ["a", "b"], indirect=True)
def test_indirect(client):
    assert client.get("/")


@pytest.mark.parametrize(NAMES, [(1, 2)])
@pytest.mark.parametrize(f"{PREFIX}_x", [1])
def test_dynamic_names(**kwargs):
    assert kwargs


@pytest.mark.parametrize(argnames="a,b", argvalues=[(1, 2)])
def test_keywords(a, b):
    assert a < b


@pytest.mark.parametrize("a,b", [(1, 2)])
@pytest.mark.parametrize("c", [3])
def test_stacked(a, b, c):
    assert a + b == c


@pytest.mark.skip
@pytest.mark.parametrize("val", [1])
def test_other_marks(val, tmp_path):
    assert val
