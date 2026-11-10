// two imports away from a test: not reached
double rate() => 0.2; // Noncompliant

double vat(double x) => x * rate();

T first<T>(List<T> xs) {
  return xs.first;
}
