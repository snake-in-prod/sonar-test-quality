from unittest.mock import patch

from app import run
from lib.clock import today


@patch("lib.clock.now")
def test_today(now):
    now.return_value = 86400
    assert today() == 1
    assert run()
