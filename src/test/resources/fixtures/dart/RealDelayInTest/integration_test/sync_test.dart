import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

// compliant: the live binding of an integration test runs on real time
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('waits for the background sync', (tester) async {
    await tester.pumpWidget(const App());
    await Future.delayed(const Duration(seconds: 3));
    sleep(const Duration(seconds: 1));
    expect(find.text('Synced'), findsOneWidget);
  });
}
