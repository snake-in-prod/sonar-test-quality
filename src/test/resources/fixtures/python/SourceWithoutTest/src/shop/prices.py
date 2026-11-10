# reached through cart.py, which a test imports
from shop.tax import rate


def price(item):
    return gross(item)


def net(item):
    return 10


def gross(item):
    return net(item) * (1 + rate())
