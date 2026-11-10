import 'package:flutter_test/flutter_test.dart';

void main() {
  test('adds', () {
    expect(add(1, 2), 3);
  });
  test('adds', () { // Noncompliant {{"adds" repeats the test on line 4}}
    expect(add(2, 2), 4);
  });

  group('parser', () {
    test('reads numbers', () {});
    testWidgets('reads numbers', (tester) async {}); // Noncompliant
    test("reads numbers", () {}); // Noncompliant
    test('reads Numbers', () {});
  });

  group('form', () {
    testWidgets('shows error', (tester) async {});
    group('inner', () {
      testWidgets('shows error', (tester) async {});
    });
  });

  group('form', () {
    testWidgets('shows error', (tester) async {}); // Noncompliant
  });

  test('joined ' 'name', () {});
  test('joined name', () {}); // Noncompliant

  group('other', () {
    test('adds', () {});
  });

  for (final c in cases) {
    test('value ${c.input}', () {});
    test('value ${c.input}', () {});
  }

  test(description, () {});
  test(description, () {});

  if (Platform.isWindows) {
    test('path', () {});
  } else {
    test('path', () {});
  }

  if (kIsWeb) test('storage', () {});
  if (!kIsWeb) test('storage', () {});
}

void sharedTests() {
  test('adds', () {});
}
