import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

void main() {
  testWidgets('waits for the animation', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: Splash()));
    await Future.delayed(const Duration(seconds: 1)); // Noncompliant {{"Future.delayed(...)" waits for real time}}
    expect(find.text('Home'), findsOneWidget);
  });

  testWidgets('typed delay', (tester) async {
    await Future<void>.delayed(const Duration(milliseconds: 500)); // Noncompliant {{"await tester.pump(duration)"}}
  });

  testWidgets('blocking sleep', (tester) async {
    sleep(const Duration(seconds: 2)); // Noncompliant {{"sleep(...)" blocks the widget test}}
  });

  testWidgets('delay in a loop and assigned', (t) async {
    while (loading) {
      await Future.delayed(const Duration(milliseconds: 100)); // Noncompliant {{"await t.pump(duration)"}}
    }
    final value = await Future<List<int>>.delayed(const Duration(seconds: 1), () => [1]); // Noncompliant
  });

  // compliant

  testWidgets('pump with a duration', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Splash()));
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle(const Duration(milliseconds: 100));
  });

  testWidgets('real async on purpose', (tester) async {
    await tester.runAsync(() async {
      await Future.delayed(const Duration(milliseconds: 10));
    });
  });

  testWidgets('a stub that simulates latency', (tester) async {
    when(() => repo.load()).thenAnswer((_) => Future.delayed(const Duration(seconds: 1), () => data));
    when(() => repo.save()).thenAnswer((_) async {
      await Future.delayed(const Duration(seconds: 1));
    });
    await tester.pump(const Duration(seconds: 1));
  });

  testWidgets('a future that is not awaited', (tester) async {
    final pending = Future.delayed(const Duration(seconds: 1));
    expect(pending, isA<Future<void>>());
  });

  testWidgets('fake timers', (tester) async {
    Timer(const Duration(seconds: 1), () => fired = true);
    await tester.pump(const Duration(seconds: 1));
  });

  testWidgets('another sleep', (tester) async {
    robot.sleep(const Duration(seconds: 1));
    await tester.binding.delayed(const Duration(seconds: 1));
  });

  test('a plain test may wait', () async {
    await Future.delayed(const Duration(milliseconds: 10));
    sleep(const Duration(milliseconds: 10));
  });
}
