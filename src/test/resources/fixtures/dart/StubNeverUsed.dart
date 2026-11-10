import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockRepo extends Mock implements Repo {}

class _StubClient extends Mock implements Client {}

void main() {
  late MockRepo shared;

  setUp(() {
    shared = MockRepo();
  });

  test('stub on a mock nobody uses', () {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user); // Noncompliant {{"repo" is stubbed}}
    final service = Service(shared);
    expect(service.current(), user);
  });

  test('mockito style stub', () {
    final repo = MockRepo();
    when(repo.load()).thenReturn(user); // Noncompliant
    expect(Service(shared).current(), user);
  });

  test('two stubs on a private mock class', () async {
    var client = _StubClient();
    when(() => client.get(any())).thenAnswer((_) async => 'a'); // Noncompliant
    when(() => client.close()).thenAnswer((_) async {}); // Noncompliant
    expect(await fetch(), 'a');
  });

  test('typed declaration, also verified', () {
    MockRepo repo = MockRepo();
    when(() => repo.load()).thenThrow(Exception()); // Noncompliant
    verifyNever(() => repo.save(any()));
    expect(run(), isNull);
  });

  test('injected', () {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    expect(Service(repo).current(), user);
  });

  testWidgets('injected in a widget', (tester) async {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    await tester.pumpWidget(App(repo: repo));
    expect(find.text('a'), findsOneWidget);
  });

  test('shared mock', () {
    when(() => shared.load()).thenReturn(user);
    expect(Service(shared).current(), user);
  });

  test('returned by another stub', () {
    final repo = MockRepo();
    final factory = MockFactory();
    when(() => repo.load()).thenReturn(user);
    when(() => factory.create()).thenReturn(repo);
    expect(Service(factory).current(), user);
  });

  test('called directly', () {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    expect(repo.load(), user);
  });

  test('registered in a locator', () {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    locator.registerSingleton<Repo>(repo);
    expect(currentUser(), user);
  });

  test('assigned to the shared field', () {
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    shared = repo;
    expect(Service(shared).current(), user);
  });

  test('a fake is not stubbed', () {
    final repo = FakeRepo();
    expect(Service(repo).current(), isNull);
  });
}
