import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:golden_toolkit/golden_toolkit.dart';

/* outer /* nested */ still a comment test('not a test', () {}); */

void main() {
  testWidgets('counter increments', (WidgetTester tester) async {
    await tester.pumpWidget(const MaterialApp(home: Counter()));
    expect(find.text('0'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.add));
    await tester.pump();
    expect(find.text('1'), findsOneWidget);
  });

  group('golden', () {
    testWidgets('matches golden', (tester) async {
      await tester.pumpWidget(const Counter());
      await expectLater(find.byType(Counter), matchesGoldenFile('counter.png'));
    }, skip: true);

    testGoldens('all sizes', (tester) async {
      await tester.pumpWidgetBuilder(const Counter());
      await screenMatchesGolden(tester, 'counter_sizes');
    });
  });

  blocTest('custom wrapper is not a test', build: () => 1);
}

class Counter extends StatelessWidget {
  const Counter({super.key});

  @override
  Widget build(BuildContext context) => Text('${1 + 1} {not a brace}');
}
