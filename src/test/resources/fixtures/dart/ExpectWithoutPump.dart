import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('tap then expect', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: Form()));
    await tester.tap(find.byKey(const Key('save')));
    expect(find.text('Saved'), findsOneWidget); // Noncompliant {{right after "tester.tap(...)" with no pump}}
  });

  testWidgets('a flag check comes first', (tester) async {
    await tester.longPress(find.byType(ListTile));
    expect(pressed, isTrue);
    expect(find.byType(PopupMenuItem), findsNWidgets(3)); // Noncompliant {{"tester.longPress(...)"}}
  });

  testWidgets('drag then a finder variable', (tester) async {
    final last = find.text('Item 99');
    await tester.drag(find.byType(ListView), const Offset(0, -3000));
    expect(last, findsNothing); // Noncompliant
  });

  testWidgets('in a block, unawaited tap', (tester) async {
    for (final key in keys) {
      tester.tap(find.byKey(key));
      expect(find.byKey(key), findsWidgets); // Noncompliant
    }
  });

  testWidgets('key event', (t) async {
    await t.sendKeyEvent(LogicalKeyboardKey.enter);
    expect(find.text('Sent'), findsOneWidget); // Noncompliant {{add "await t.pump()"}}
    expect(find.text('Draft'), findsNothing);
  });

  // compliant

  testWidgets('pump in between', (tester) async {
    await tester.tap(find.byKey(const Key('save')));
    await tester.pump();
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('pumpAndSettle in between', (tester) async {
    await tester.tap(find.byKey(const Key('save')));
    await tester.pumpAndSettle();
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('only state and mocks after the tap', (tester) async {
    await tester.tap(find.byType(ElevatedButton));
    expect(pressed, isTrue);
    expect(counter.value, 1);
  });

  testWidgets('enterText updates the controller', (tester) async {
    await tester.enterText(find.byType(TextField), 'abc');
    expect(find.text('abc'), findsOneWidget);
  });

  testWidgets('a helper may pump', (tester) async {
    await tester.tap(find.byKey(const Key('save')));
    await settle(tester);
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('the expect is in another block', (tester) async {
    if (wide) {
      await tester.tap(find.byKey(const Key('save')));
    }
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('expect before the tap', (tester) async {
    expect(find.text('Saved'), findsNothing);
    await tester.tap(find.byKey(const Key('save')));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('a tap on another object', (tester) async {
    await robot.tap(find.byKey(const Key('save')));
    expect(find.text('Saved'), findsOneWidget);
  });

  test('a plain test', () async {
    await tester.tap(find.byKey(const Key('save')));
    expect(find.text('Saved'), findsOneWidget);
  });
}
