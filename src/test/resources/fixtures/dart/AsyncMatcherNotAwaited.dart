import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('loads items', (tester) async {
    await tester.pumpWidget(const App());
    expect(repo.load(), completion(isNotEmpty)); // Noncompliant {{"completion" settles after this widget test may have ended}}
  });

  testWidgets('saves', (tester) async {
    expect(save(), completes); // Noncompliant
  });

  testWidgets('emits states', (WidgetTester tester) async {
    await tester.pumpWidget(const App());
    if (bloc != null) {
      expect(bloc.stream, emitsInOrder([Loading(), Loaded()])); // Noncompliant
    }
  });

  testWidgets('fails to load', (tester) async {
    expect(repo.load(), throwsA(isA<StateError>())); // Noncompliant
  });

  testWidgets('bad input', (tester) async {
    expect(parser.parseAsync('x'), throwsFormatException); // Noncompliant
  });

  testGoldens('closes', (tester) async {
    expect(controller.stream, emitsDone); // Noncompliant
  });

  // compliant

  testWidgets('awaited expectLater', (tester) async {
    await expectLater(repo.load(), completion(isNotEmpty));
  });

  testWidgets('throws on a closure may be sync', (tester) async {
    expect(() => parser.parse('x'), throwsFormatException);
  });

  testWidgets('throwsA on a variable may be a function', (tester) async {
    expect(load, throwsA(isA<StateError>()));
  });

  testWidgets('sync matcher', (tester) async {
    expect(find.text('a'), findsOneWidget);
  });

  testWidgets('inside runAsync', (tester) async {
    await tester.runAsync(() async {
      expect(repo.load(), completion(isNotEmpty));
    });
  });

  testWidgets('custom matcher name', (tester) async {
    expect(value, completionOf(1));
  });

  test('plain tests wait for async matchers', () {
    expect(repo.load(), completion(isNotEmpty));
    expect(stream, emitsInOrder([1, 2]));
  });

  testWidgets('matcher nested in another', (tester) async {
    expect(values, everyElement(isNotNull));
  });
}
