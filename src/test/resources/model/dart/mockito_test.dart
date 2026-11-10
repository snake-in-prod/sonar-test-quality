import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:test/test.dart';

import 'mockito_test.mocks.dart';

class Repo {
  String load() => '';
}

class FakeRepo extends Fake implements Repo {
  @override
  String load() => 'fake';
}

@GenerateMocks([Repo])
void main() {
  test('loads from the repo', () {
    final repo = MockRepo();
    when(repo.load()).thenReturn('x');
    expect(repo.load(), 'x');
    verify(repo.load()).called(1);
    verifyNoMoreInteractions(repo);
  });

  test('uses a fake', () {
    final repo = FakeRepo();
    expect(repo.load(), 'fake');
  });

  test('never called', () {
    final repo = MockRepo();
    verifyNever(repo.load());
  });
}
