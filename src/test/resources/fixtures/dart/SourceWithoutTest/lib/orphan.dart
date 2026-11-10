import 'dart:math';

mixin Logger {
  void log(String m) { // Noncompliant {{add tests for its 3 functions}}
    print(m);
  }
}

class Report with Logger {
  @override
  String toString() => 'report';

  int get size => 0;

  Stream<int> rows() async* {
    yield max(1, 2);
  }
}
