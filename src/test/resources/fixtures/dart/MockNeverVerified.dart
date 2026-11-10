import 'package:bloc_test/bloc_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockRepo extends Mock implements Repo {}

class _Cache extends Mock implements Cache {}

void main() {
  late MockRepo shared;

  setUp(() {
    shared = MockRepo();
  });

  test('injected and forgotten', () {
    final api = MockApi(); // Noncompliant {{"api" is injected but never stubbed or verified}}
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    final service = Service(api, repo);
    expect(service.current(), user);
  });

  testWidgets('injected into a widget by name', (tester) async {
    final analytics = MockAnalytics(); // Noncompliant
    final auth = MockAuth();
    when(() => auth.user).thenReturn(user);
    await tester.pumpWidget(App(auth: auth, analytics: analytics));
    expect(find.text('hi'), findsOneWidget);
  });

  test('another mock is verified', () {
    var cache = _Cache(); // Noncompliant
    final repo = MockRepo();
    Store(cache, repo).save(user);
    verify(() => repo.save(user)).called(1);
  });

  test('typed declaration', () {
    MockLogger logger = MockLogger(); // Noncompliant
    final service = Service(logger: logger, repo: shared);
    service.run();
    verify(() => shared.run()).called(1);
  });

  test('initial state needs only a dummy', () {
    final api = MockApi();
    expect(LoginBloc(api).state, LoginInitial());
  });

  test('stubbed', () {
    final api = MockApi();
    when(() => api.fetch()).thenAnswer((_) async => 1);
    expect(Service(api).value, 1);
  });

  test('verified', () {
    final api = MockApi();
    Service(api).send();
    verify(() => api.send()).called(1);
  });

  test('no interactions', () {
    final api = MockApi();
    final repo = MockRepo();
    when(() => repo.load()).thenReturn(user);
    Service(api, repo).current();
    verifyZeroInteractions(api);
  });

  test('handed to a helper', () {
    final api = MockApi();
    arrangeSuccess(api);
    when(() => shared.load()).thenReturn(user);
    expect(Service(api, shared).current(), user);
  });

  test('provided by value', () {
    final api = MockApi();
    when(() => shared.load()).thenReturn(user);
    final widget = Provider.value(value: api, child: App(repo: shared));
    expect(widget, isNotNull);
  });

  test('inline dummy', () {
    when(() => shared.load()).thenReturn(user);
    expect(Service(MockApi(), shared).current(), user);
  });

  test('called directly', () {
    final api = MockApi();
    when(() => shared.load()).thenReturn(user);
    api.connect();
    expect(Service(api, shared).current(), user);
  });

  test('never used', () {
    final api = MockApi();
    when(() => shared.load()).thenReturn(user);
    expect(Service(shared).current(), user);
  });

  blocTest<UserBloc, UserState>(
    'injected in build and forgotten',
    build: () {
      final api = MockApi(); // Noncompliant
      final repo = MockRepo();
      when(() => repo.load()).thenReturn(user);
      return UserBloc(api, repo);
    },
    expect: () => [isA<Loaded>()],
  );

  blocTest<UserBloc, UserState>(
    'shared mocks stubbed and verified',
    build: () => UserBloc(shared),
    act: (bloc) => bloc.add(Load()),
    expect: () => [isA<Loaded>()],
    verify: (_) => verify(() => shared.load()).called(1),
  );
}
