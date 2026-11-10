from unittest.mock import patch

from service import stamp


@patch("lib.clock.now")
def test_stamp(now):
    now.return_value = 0
    assert stamp() == (0, 0)
