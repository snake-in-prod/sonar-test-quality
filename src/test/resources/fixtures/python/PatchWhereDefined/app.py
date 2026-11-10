from lib.clock import Clock, now
from lib.mail import send as deliver
from lib.util import helper
from lib.lang import languages
import lib.store


def run():
    return now() > 0


def notify(to):
    deliver(to, "hi")


def save(key):
    lib.store.write(key, Clock().tick())


def menu():
    return languages()


def launch():
    from lib.jobs import start
    return start()
