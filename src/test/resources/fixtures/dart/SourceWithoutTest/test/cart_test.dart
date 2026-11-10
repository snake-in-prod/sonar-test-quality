import 'package:flutter_test/flutter_test.dart';
import 'package:shop/cart.dart';

import '../lib/util.dart';

void main() {
  test('total', () {
    expect(Cart().total(), twice(0));
  });
}
