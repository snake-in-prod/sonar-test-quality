# two imports away from a test: not reached
def rate():  # Noncompliant {{No test imports this file}}
    return 0.2


def vat(x):
    return x * rate()


def exempt(x):
    return x
