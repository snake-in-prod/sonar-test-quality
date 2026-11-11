import 'package:flutter_test/flutter_test.dart';

void appWidgetTest(String description, Future<void> Function(WidgetTester, AppHelper) test) {
  testWidgets(description, (widgetTester) async {
    await AppHelper.loadApp(widgetTester);
    await test(widgetTester, AppHelper(widgetTester));
  });
}
