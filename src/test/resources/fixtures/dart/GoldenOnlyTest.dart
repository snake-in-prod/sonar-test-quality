import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('home screen', (tester) async {
    await tester.pumpWidget(const App());
    await expectLater(find.byType(App), matchesGoldenFile('home.png')); // Noncompliant {{only compares a golden file}}
  });

  testWidgets('two goldens', (WidgetTester tester) async {
    await tester.pumpWidget(const App());
    await expectLater(find.byType(App), matchesGoldenFile('light.png')); // Noncompliant
    await tester.tap(find.byIcon(Icons.dark_mode));
    await tester.pumpAndSettle();
    await expectLater(find.byType(App), matchesGoldenFile('dark.png'));
  });

  group('sizes', () {
    testWidgets('small', (tester) async {
      tester.view.physicalSize = const Size(320, 480);
      await tester.pumpWidget(const App());
      expect(find.byType(App), matchesGoldenFile('small.png')); // Noncompliant
    });
  });

  testWidgets('arrow', (tester) => expectLater(find.byType(App), matchesGoldenFile('a.png'))); // Noncompliant

  // compliant

  testWidgets('golden and content', (tester) async {
    await tester.pumpWidget(const App());
    expect(find.text('Welcome'), findsOneWidget);
    await expectLater(find.byType(App), matchesGoldenFile('home.png'));
  });

  testWidgets('golden and verify', (tester) async {
    await tester.pumpWidget(const App());
    await expectLater(find.byType(App), matchesGoldenFile('home.png'));
    verify(analytics.logScreen('home')).called(1);
  });

  testWidgets('helper may check more', (tester) async {
    await pumpHome(tester);
    await expectLater(find.byType(App), matchesGoldenFile('home.png'));
  });

  testWidgets('no golden', (tester) async {
    await tester.pumpWidget(const App());
    expect(find.text('Welcome'), findsOneWidget);
  });

  testGoldens('toolkit golden tests are golden by design', (tester) async {
    await tester.pumpWidgetBuilder(const App());
    await screenMatchesGolden(tester, 'app');
  });

  test('unit test', () async {
    await expectLater(render(), matchesGoldenFile('unit.png'));
  });

  testWidgets('no checks at all', (tester) async {
    await tester.pumpWidget(const App());
  });
}
