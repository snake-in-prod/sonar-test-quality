import time


class Clock:
    def tick(self):
        return time.time()


def now():
    return time.time()


def today():
    return int(now() // 86400)
