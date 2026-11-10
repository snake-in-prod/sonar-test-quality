import 'package:test/test.dart';

void main() {
  test('adds', () {
    expect(1 + 1, equals(2));
  });

  test('arrow body', () => expect(r'$raw', isNotEmpty));

  test(
    'joined ' 'description',
    () async {
      final value = await Future.value('${"nested ${'deep'}"}');
      expect(value, 'nested deep');
    },
  );

  group('parser', () {
    group('numbers', () {
      test('underscores', () {
        expect(1_000, 1000);
        expect(0xFF, 255);
      });

      test('pending', () {}, skip: 'waits for the fix');
    });

    test('conditional skip is not a skip', () {
      expect(true, isTrue);
    }, skip: isWindows);
  }, skip: false);

  group('skipped group', () {
    test('inherits skip', () {
      expect(1, 1);
    });
  }, skip: true);
}

bool get isWindows => false;

void test2(String x) {}
