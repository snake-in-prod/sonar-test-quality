import 'package:flutter_test/flutter_test.dart';

void main() {
  test('anything', () {
    expect(parse('1'), anything); // Noncompliant {{"anything" matches every value}}
  });

  test('isA dynamic', () {
    expect(result, isA<dynamic>()); // Noncompliant
    expect(result, isA<Object?>()); // Noncompliant
  });

  testWidgets('finder is never null', (tester) async {
    await tester.pumpWidget(const App());
    expect(find.byKey(const Key('save')), isNotNull); // Noncompliant {{use findsOneWidget}}
    expect(find.text('Saved').first, isA<Finder>()); // Noncompliant
  });

  test('constructor is never null', () {
    expect(Parser(), isNotNull); // Noncompliant
    expect(const Config(debug: true), isNotNull); // Noncompliant
  });

  test('constant values', () {
    expect(true, isTrue); // Noncompliant {{"true" is a constant}}
    expect(1, equals(1)); // Noncompliant
    expect('ok', 'ok'); // Noncompliant
    expect(null, isNull); // Noncompliant
  });

  test('compared with itself', () async {
    final user = await repo.load();
    expect(user.name, user.name); // Noncompliant {{compared with itself}}
    await expectLater(user, equals(user)); // Noncompliant
  });

  // compliant

  test('real matchers', () {
    expect(parse('1'), 1);
    expect(result, isA<Parser>());
    expect(result, isA<Object>());
    expect(() => parse('x'), throwsA(anything));
    expect(user, isA<User>().having((u) => u.name, 'name', anything));
  });

  testWidgets('finders with real matchers', (tester) async {
    expect(find.byKey(const Key('save')), findsOneWidget);
    expect(tester.widget(find.byType(Text)), isNotNull);
    expect(finder, isNotNull);
  });

  test('named constructor or factory may be nullable', () {
    expect(Parser.tryParse('x'), isNotNull);
    expect(parser(), isNotNull);
  });

  test('constants with a different value fail', () {
    expect(true, isFalse);
    expect(null, isNotNull);
    expect('$name', 'x');
  });

  test('two different values', () {
    expect(user.name, other.name);
    expect(rng.next(), equals(rng.next()));
    expect(list[0], list[0]);
    expect(double.nan, double.nan);
  });

  test('not an expect', () {
    check(x, anything);
    matcher.expect(x, anything);
  });
}
