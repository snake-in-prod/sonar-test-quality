def languages():
    return ["en", "de"]


def codes():
    return [code for code in languages() if code != "en"]
