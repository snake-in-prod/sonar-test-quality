import 'package:flutter_test/flutter_test.dart';

void slideshowTest(String description, Future<void> Function(WidgetTester tester, Show show) body, {bool repeat = true}) {
  testWidgets(description, (tester) async {
    final show = Show(repeat: repeat);
    await tester.pump();
    await body(tester, show);
  });
}

void unitTest(String name, dynamic Function() body) => test(name, body);

void check(String name, int expected) {
  test(name, () {
    expect(compute(), expected);
  });
}

void main() {
  group('slideshow', () {
    slideshowTest('advances', (tester, show) async {
      await tester.pump(const Duration(seconds: 3));
      expect(show.index, 1);
    });

    slideshowTest(
      'pauses',
      (tester, show) async {
        show.pause();
        expect(show.playing, isFalse);
        expect(show.index, 0);
      },
      repeat: false,
    );
  });

  unitTest('computes', () {
    expect(compute(), 2);
  });

  check('parameterized', 2);

  final name = 'computed';
  slideshowTest(name, (tester, show) async {});
}
