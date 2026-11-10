import shop.discounts as d
from shop.cart import Cart
from shop.star import *

from .helpers import make_item


def test_total():
    cart = Cart()
    cart.add(make_item())
    assert cart.total() == d.apply(10) + half(0)
