def build(name):
    return {"name": name}


def build_many(*names):
    return [build(n) for n in names]


def empty():
    return {}
