import 'package:flutter_test/flutter_test.dart';

class Robot {
  Robot(this.tester);

  final WidgetTester tester;

  Future<void> tap(Finder f) => tester.tap(f);
}

void robotTest(String description, Future<void> Function(Robot robot, WidgetTester tester) body) {
  testWidgets(description, (tester) async {
    await body(Robot(tester), tester);
  });
}
