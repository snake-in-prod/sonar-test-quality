import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('stream emits', (tester) async {
    final controller = StreamController<int>();
    expectLater(controller.stream, emits(1)); // Noncompliant {{"expectLater(...)" is not awaited}}
    controller.add(1);
  });

  testWidgets('async test forgets await', (tester) async {
    expectLater(load(), completion(isNotEmpty)); // Noncompliant
  });

  testWidgets('golden without await', (tester) async {
    await tester.pumpWidget(const App());
    expectLater( // Noncompliant
      find.byType(App),
      matchesGoldenFile('app.png'),
    );
  });

  group('nested blocks', () {
    testWidgets('inside if', (tester) async {
      if (enabled) {
        expectLater(load(), completes); // Noncompliant
      } else {
        await expectLater(load(), throwsA(isA<StateError>()));
      }
    });

    testWidgets('inside try and for', (tester) async {
      for (final s in streams) {
        expectLater(s, emitsDone); // Noncompliant
      }
      try {
        expectLater(load(), completes); // Noncompliant
      } finally {
        await cleanUp();
      }
    });
  });

  // compliant

  // package:test keeps a plain test alive until expectLater settles: subscribe first, then add events
  test('plain test subscribes before adding events', () {
    final controller = StreamController<int>();
    expectLater(controller.stream, emitsInOrder([1, emitsDone]));
    controller
      ..add(1)
      ..close();
  });

  test('plain async test', () async {
    expectLater(load(), completion(isNotEmpty));
  });

  testWidgets('awaited', (tester) async {
    await expectLater(load(), completion(isNotEmpty));
  });

  testWidgets('returned', (tester) {
    return expectLater(load(), completes);
  });

  testWidgets('arrow body', (tester) => expectLater(load(), completes));

  testWidgets('kept in a future and awaited later', (tester) async {
    final check = expectLater(stream, emitsInOrder([1, 2]));
    push(1);
    push(2);
    await check;
  });

  testWidgets('explicitly unawaited', (tester) async {
    unawaited(expectLater(stream, emits(1)));
  });

  testWidgets('collected', (tester) async {
    await Future.wait([expectLater(a, emits(1)), expectLater(b, emits(2))]);
  });

  testWidgets('inside a callback', (tester) async {
    await run(() async {
      expectLater(stream, emits(1));
    });
  });

  testWidgets('sync expect', (tester) async {
    expect(value, 1);
  });

  testWidgets('chained', (tester) async {
    expectLater(stream, emits(1)).then((_) => done());
  });

  testWidgets('awaited golden', (tester) async {
    await tester.pumpWidget(const App());
    await expectLater(find.byType(App), matchesGoldenFile('app.png'));
  });
}

Future<void> helper() async {
  expectLater(stream, emits(1));
}
