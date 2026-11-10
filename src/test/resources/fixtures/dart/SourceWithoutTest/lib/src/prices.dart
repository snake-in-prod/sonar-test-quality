// reached through cart.dart, which a test imports
import 'package:shop/src/tax.dart';

int price(int x) => gross(x);

int net(int x) {
  return x;
}

int gross(int x) => (net(x) * (1 + rate())).round();
