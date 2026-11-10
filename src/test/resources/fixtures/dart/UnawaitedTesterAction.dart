import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('taps without waiting', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: Form()));
    tester.tap(find.byKey(const Key('save'))); // Noncompliant {{"tester.tap(...)" is not awaited}}
    await tester.pump();
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('pumps without waiting', (tester) async {
    tester.pumpWidget(const MaterialApp(home: Form())); // Noncompliant {{"tester.pumpWidget(...)"}}
    expect(find.byType(Form), findsOneWidget);
  });

  group('inside blocks', () {
    testWidgets('enters text in a branch', (tester) async {
      await tester.pumpWidget(const MaterialApp(home: Form()));
      if (useKeyboard) {
        tester.enterText(find.byType(TextField), 'abc'); // Noncompliant
      } else {
        tester.pumpAndSettle(); // Noncompliant
      }
      for (final key in keys) {
        tester.longPress(find.byKey(key)); // Noncompliant
      }
      try {
        tester.drag(find.byType(ListView), const Offset(0, -300)); // Noncompliant
      } finally {
        await tester.pump();
      }
    });
  });

  testWidgets('a renamed tester', (WidgetTester t) async {
    t.pump(const Duration(seconds: 1)); // Noncompliant {{"t.pump(...)"}}
  });

  // compliant

  testWidgets('awaited actions', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: Form()));
    await tester.tap(find.byKey(const Key('save')));
    await tester.enterText(find.byType(TextField), 'abc');
    await tester.pumpAndSettle();
    expect(find.text('Saved'), findsOneWidget);
  });

  testWidgets('returned and explicitly unawaited', (tester) async {
    unawaited(tester.pump());
    final done = tester.pumpAndSettle();
    await done;
    return tester.pump();
  });

  testWidgets('synchronous tester members', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Form()));
    tester.view.physicalSize = const Size(400, 800);
    tester.takeException();
    tester.widget<Text>(find.byType(Text));
    tester.binding.scheduleFrame();
  });

  testWidgets('another object with the same method names', (tester) async {
    controller.tap(1);
    robot.pump();
    tester.tap;
  });

  testWidgets('arrow body', (tester) => tester.pumpWidget(const Form()));

  testWidgets('closures are not the test sequence', (tester) async {
    final go = () {
      tester.tap(find.text('go'));
    };
    await tester.runAsync(() async {
      tester.pump();
    });
    await tester.pumpWidget(Builder(builder: (context) {
      tester.pump();
      return const SizedBox();
    }));
  });

  test('a plain test', () {
    tester.tap(find.text('x'));
  });
}
