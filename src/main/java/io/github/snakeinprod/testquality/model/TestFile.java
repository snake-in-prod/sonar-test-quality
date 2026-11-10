package io.github.snakeinprod.testquality.model;

import java.util.List;

/** The tests of one file, as the language's extractor found them. */
public record TestFile(List<TestCase> tests) {

  public static final TestFile EMPTY = new TestFile(List.of());

  public int assertions() {
    return tests.stream().mapToInt(TestCase::assertions).sum();
  }

  public long withoutAssertion() {
    return tests.stream().filter(t -> !t.skipped() && t.assertions() == 0).count();
  }

  public int mocks() {
    return tests.stream().mapToInt(TestCase::mocks).sum();
  }

  public long mockHeavy() {
    return tests.stream().filter(t -> t.mocks() > t.assertions()).count();
  }

  public long skipped() {
    return tests.stream().filter(TestCase::skipped).count();
  }
}
