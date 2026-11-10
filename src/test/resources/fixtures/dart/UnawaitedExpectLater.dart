import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('stream emits', () {
    final controller = StreamController<int>();
    expectLater(controller.stream, emits(1)); // Noncompliant {{"expectLater(...)" is not awaited}}
    controller.add(1);
  });

  test('async test forgets await', () async {
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
    test('inside if', () async {
      if (enabled) {
        expectLater(load(), completes); // Noncompliant
      } else {
        await expectLater(load(), throwsA(isA<StateError>()));
      }
    });

    test('inside try and for', () async {
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

  test('awaited', () async {
    await expectLater(load(), completion(isNotEmpty));
  });

  test('returned', () {
    return expectLater(load(), completes);
  });

  test('arrow body', () => expectLater(load(), completes));

  test('kept in a future and awaited later', () async {
    final check = expectLater(stream, emitsInOrder([1, 2]));
    push(1);
    push(2);
    await check;
  });

  test('explicitly unawaited', () {
    unawaited(expectLater(stream, emits(1)));
  });

  test('collected', () async {
    await Future.wait([expectLater(a, emits(1)), expectLater(b, emits(2))]);
  });

  test('inside a callback', () async {
    await run(() async {
      expectLater(stream, emits(1));
    });
  });

  test('sync expect', () {
    expect(value, 1);
  });

  test('chained', () async {
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
