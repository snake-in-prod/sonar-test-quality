@Skip() // Noncompliant {{"@Skip()" gives no reason}}
library;

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a', () {}, skip: true); // Noncompliant {{"skip: true" gives no reason}}
  testWidgets('b', (tester) async {}, skip: true); // Noncompliant
  group('c', () {
    test('d', () {});
  }, skip: true); // Noncompliant
  testGoldens('e', (tester) async {}, skip: ''); // Noncompliant
  test(
    'multi line',
    () {},
    skip: true, // Noncompliant
  );

  test('reason', () {}, skip: 'flaky on CI');
  test('false', () {}, skip: false);
  test('conditional', () {}, skip: isWindows);
  test('negated', () {}, skip: !Platform.isLinux);
  test('ternary', () {}, skip: isWindows ? 'no windows' : false);
  test('interpolated', () {}, skip: 'blocked by $issue');
  build(skip: true);
  runner.test('x', () {}, skip: true);
}

void test(String name, Function body, {Object? skip = true}) {}
