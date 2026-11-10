import time
import time as clock
from time import sleep
import unittest


def test_waits_for_cache():
    cache.set("k", 1, ttl=1)
    time.sleep(1.5)  # Noncompliant {{"time.sleep(1.5)" slows the test}}
    assert cache.get("k") is None


def test_imported_sleep():
    start()
    sleep(2)  # Noncompliant
    assert done()


def test_aliased_module():
    clock.sleep(0.5)  # Noncompliant


def test_in_loop():
    for _ in range(3):
        if ready():
            break
        time.sleep(DELAY)  # Noncompliant


class TestWorker(unittest.TestCase):
    def test_worker(self):
        run(worker)
        time.sleep(  # Noncompliant
            1)
        self.assertTrue(worker.done)


# compliant


def test_yields_only():
    time.sleep(0)
    assert done()


def test_slow_work_in_nested_function():
    def slow():
        time.sleep(2)
    assert run_with_timeout(slow, 1) is None


def test_slow_work_in_lambda():
    assert run_with_timeout(lambda: time.sleep(2), 1) is None


def test_other_sleep(fake_clock):
    fake_clock.sleep(10)
    assert expired()


def test_asyncio_style(loop):
    loop.sleep(1)
    assert done()


def test_sleep_passed_not_called():
    retry(fn, wait=time.sleep)
    assert done()


def helper():
    time.sleep(1)


class Helper:
    def wait(self):
        time.sleep(1)
