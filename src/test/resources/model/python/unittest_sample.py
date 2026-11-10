import unittest
from unittest import mock, TestCase as Base
from unittest.mock import patch, MagicMock


class Helper:
    def test_not_collected(self):
        assert True


class BaseCase(Base):
    def setUp(self):
        self.client = MagicMock()


class UserTests(BaseCase):
    """Collected through the local base class."""

    def test_create(self):
        self.assertEqual(create(), 1)
        self.assertTrue(created())

    @patch("app.mail.send")
    @mock.patch.object(Service, "call")
    def test_send(self, call, send):
        run()
        send.assert_called_once_with("x")
        call.assert_not_called()

    def test_skip_at_start(self):
        """Docstring first."""
        self.skipTest("not ready")
        self.assertEqual(1, 1)

    @unittest.skip("broken")
    def test_skipped(self):
        self.fail("never")

    def helper(self):
        self.assertEqual(1, 1)

    async def test_async(self):
        with self.assertRaises(ValueError):
            await parse("x")


@unittest.skipIf(True, "platform")
class SkippedSuite(unittest.IsolatedAsyncioTestCase):
    def test_one(self):
        m = mock.Mock()
        m.assert_called()
