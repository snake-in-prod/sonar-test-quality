import 'package:bloc_test/bloc_test.dart';
import 'package:mockito/mockito.dart';
import 'package:mocktail/mocktail.dart';
import 'package:test/test.dart';

class Repo {
  int get count => 0;
  String get name => '';
  void save(User u) {}
  void delete(User u) {}
  User load() => User();
}

class MockRepo extends Mock implements Repo {}

void main() {
  late MockRepo repo;
  final api = MockApi();

  setUp(() {
    repo = MockRepo();
  });

  test('method reference in verify', () {
    service.save(user);
    verify(repo.save); // Noncompliant {{"repo.save" in verify is a method reference}}
  });

  test('method reference with called', () {
    service.save(user);
    verify(repo.save).called(1); // Noncompliant
  });

  test('method reference in verifyNever', () {
    verifyNever(repo.delete); // Noncompliant
  });

  test('method reference in when', () {
    when(repo.load).thenReturn(user); // Noncompliant {{stubs nothing}}
    expect(service.current(), user);
  });

  test('closure returns the method reference', () {
    service.save(user);
    verify(() => repo.save); // Noncompliant
  });

  test('the mock itself', () {
    service.run();
    verify(repo); // Noncompliant {{is the mock itself}}
  });

  test('the mock itself in a closure', () {
    verify(() => api); // Noncompliant
  });

  test('called', () {
    verify(repo.save(user));
  });

  test('called with any and count', () {
    verify(repo.delete(any)).called(1);
  });

  test('closure with call', () {
    verify(() => repo.save(any()));
  });

  test('getters', () {
    when(repo.count).thenReturn(1);
    verify(repo.count);
    verify(() => repo.name);
  });

  test('unknown variable', () {
    verify(callback);
  });

  test('in order', () {
    verifyInOrder([repo.load(), repo.save(user)]);
  });

  test('block closure', () {
    verify(() {
      return repo.load();
    });
  });

  test('member named verify', () {
    signer.verify(repo.save);
  });

  test('two arguments', () {
    verify(signature, key);
  });

  test('closure with parameter', () {
    when((x) => repo.save);
  });

  blocTest<UserBloc, UserState>(
    'saves the user',
    build: () => UserBloc(repo),
    act: (bloc) => bloc.add(Save(user)),
    expect: () => [isA<Saved>()],
    verify: (_) {
      verify(() => repo.save); // Noncompliant
      verify(() => repo.save(user)).called(1);
      verify(repo.delete(user));
    },
  );

  blocTest<UserBloc, UserState>(
    'arrow verify',
    build: () => UserBloc(repo),
    verify: (_) => verify(repo.save), // Noncompliant
  );
}
