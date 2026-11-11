import 'package:flutter_test/flutter_test.dart';

// a wrapper in its own file: its testWidgets call is not a test, the calls in other files are
void appWidgetTest(String description, Future<void> Function(WidgetTester, AppHelper) test) {
  testWidgets(description, (widgetTester) async {
    await AppHelper.loadApp(widgetTester);
    await test(widgetTester, AppHelper(widgetTester));
  });
}
