import importlib
from unittest.mock import patch

import app


def test_reloaded():
    with patch("lib.clock.now", return_value=5):
        importlib.reload(app)
        assert app.run()
