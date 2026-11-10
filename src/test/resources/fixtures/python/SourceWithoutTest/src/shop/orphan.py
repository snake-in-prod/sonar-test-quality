import os


class Report:
    def render(self):  # Noncompliant
        return os.linesep.join(self.rows())

    def rows(self):
        return []

    @staticmethod
    def title():
        return "report"
