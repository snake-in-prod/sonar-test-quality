import 'package:mocktail/mocktail.dart';
import 'package:test/test.dart';

class Api {
  Future<int> fetch(String id) async => 0;
}

class _MockApi extends Mock implements Api {}

void main() {
  late _MockApi api;

  setUp(() {
    api = _MockApi();
  });

  test('fetches', () async {
    final local = _MockApi();
    when(() => local.fetch(any())).thenAnswer((_) async => 1);
    expect(await local.fetch('a'), 1);
    verify(() => local.fetch('a')).called(1);
  });

  test('no checks', () async {
    when(() => api.fetch(any())).thenAnswer((_) async => 2);
    await api.fetch('b');
  });
}
