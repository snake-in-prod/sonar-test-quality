import logging
import unittest

log = logging.getLogger(__name__)


def test_broad_pass():
    try:
        assert compute() == 3
    except Exception:  # Noncompliant {{"except Exception" catches the AssertionError}}
        pass


def test_bare_except_logs():
    try:
        result = compute()
        assert result.ok
    except:  # Noncompliant {{"except:"}}
        log.warning("compute failed")


def test_assertion_error_in_tuple():
    try:
        sender.assert_called_once_with("hi")
    except (KeyError, AssertionError) as e:  # Noncompliant {{"except (KeyError, AssertionError)"}}
        print(e)


def test_nested_in_with(client):
    with client:
        try:
            if client.ready:
                assert client.ping()
        except BaseException:  # Noncompliant
            return


class TestUnit(unittest.TestCase):
    def test_self_assert(self):
        try:
            self.assertEqual(total(), 10)
        except AssertionError:  # Noncompliant
            ...


# compliant


def test_narrow_exception():
    try:
        assert compute() == 3
    except ValueError:
        pass


def test_reraises():
    try:
        assert compute() == 3
    except Exception:
        cleanup()
        raise


def test_collects_errors():
    errors = []
    try:
        assert compute() == 3
    except AssertionError as e:
        errors.append(e)
    assert not errors


def test_expected_failure_guarded():
    try:
        assert_valid(bad)
    except AssertionError:
        pass
    else:
        raise RuntimeError("should fail")


def test_expected_assertion_with_else():
    try:
        assert check(bad)
    except AssertionError:
        pass
    else:
        pytest.fail("check should fail")


def test_retry_loop():
    for _ in range(10):
        try:
            assert service.ready()
            break
        except AssertionError:
            time.sleep(1)
    assert service.ready()


def test_either_order():
    try:
        assert lines == sorted_by_int
    except AssertionError:
        assert lines == sorted_by_str


def test_no_assertion_in_try():
    try:
        compute()
    except Exception:
        pass
    assert done


def test_handler_fails():
    try:
        assert compute() == 3
    except Exception as e:
        pytest.fail(str(e))


def test_assertion_in_nested_function():
    try:
        def check():
            assert compute()
        register(check)
    except Exception:
        pass


def helper():
    try:
        assert compute()
    except Exception:
        pass
