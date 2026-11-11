import 'package:app/testing.dart';
import 'package:flutter_test/flutter_test.dart';

import 'test_utils/general_helper.dart';

void main() {
  group('login', () {
    appWidgetTest('logs in', (tester, helper) async {
      await tester.tap(find.text('Login'));
      expect(find.text('Welcome'), findsOneWidget);
    });

    appWidgetTest('rejects a wrong password', (tester, helper) async {
      expect(find.text('Wrong'), findsOneWidget);
      expect(find.text('Welcome'), findsNothing);
    });
  });

  unitTest('validates the email', () {
    expect(validate('a@b'), isTrue);
  });

  final name = 'computed';
  appWidgetTest(name, (tester, helper) async {});
}
