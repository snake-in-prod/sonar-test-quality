import 'src/prices.dart';

class Cart {
  final List<int> items = [];

  void add(int x) {
    items.add(x);
  }

  int total() => items.fold(0, (a, b) => a + price(b));

  Future<void> save() async {}
}
