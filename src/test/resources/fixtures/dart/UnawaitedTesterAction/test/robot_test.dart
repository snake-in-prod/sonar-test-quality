import 'package:app/testing.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // imported through package:app/testing.dart, which exports the wrapper
  robotTest('the tester comes second', (robot, tester) async {
    tester.pumpAndSettle(); // Noncompliant {{"tester.pumpAndSettle(...)"}}
    robot.tap(find.text('go'));
  });
}
