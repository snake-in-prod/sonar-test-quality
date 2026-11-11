import 'package:flutter_test/flutter_test.dart';

import 'test_utils/app_helper.dart';

void main() {
  group('login', () {
    appWidgetTest('logs in', (tester, helper) async {
      tester.tap(find.text('Login')); // Noncompliant {{"tester.tap(...)" is not awaited}}
      await helper.waitForTimeline();
    });

    appWidgetTest('enters the server', (tester, helper) async {
      await tester.enterText(find.byKey(const Key('server')), 'https://example.org');
      helper.tap(find.text('Next'));
    });
  });
}
