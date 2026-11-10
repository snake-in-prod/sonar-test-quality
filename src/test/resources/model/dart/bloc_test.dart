import 'package:bloc_test/bloc_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:test/test.dart';

class _MockRepo extends Mock implements Repo {}

@isTest
void blocTest<B extends BlocBase<State>, State>(String description, {required B Function() build}) {}

void main() {
  group('CounterBloc', () {
    blocTest<CounterBloc, int>(
      'emits [] when nothing is added',
      build: () => CounterBloc(),
      expect: () => const <int>[],
    );

    blocTest<CounterBloc, List<int>>(
      'skips the seed',
      build: () {
        final repo = _MockRepo();
        when(() => repo.load()).thenReturn(1);
        return CounterBloc(repo);
      },
      seed: () => [0],
      act: (bloc) => bloc.add(Increment()),
      skip: 1,
      wait: const Duration(milliseconds: 10),
      expect: () => [
        [1],
      ],
      errors: () => [isA<Exception>()],
      verify: (bloc) {
        verify(() => repo.load()).called(1);
        expect(bloc.state, [1]);
      },
    );

    blocTest('only acts', build: () => CounterBloc(), act: (bloc) => bloc.add(Increment()));

    group('skipped', () {
      blocTest<CounterBloc, int>('inherits skip', build: () => CounterBloc(), expect: () => [1]);
    }, skip: true);
  });
}
