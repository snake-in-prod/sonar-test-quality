package io.github.snakeinprod.testquality.reports;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Test runs collected from JUnit and Dart reports: durations, p95, slowest and flaky tests. */
public final class Results {

  public enum Outcome { PASSED, FAILED, SKIPPED }

  private final Map<String, Long> durations = new HashMap<>();
  private final Map<String, Set<Outcome>> outcomes = new HashMap<>();

  /** The identity of a test: {@code prefix > name}, or the name alone when it already starts with the prefix. */
  public static String id(String prefix, String name) {
    return prefix == null || prefix.isEmpty() || name.startsWith(prefix) ? name : prefix + " > " + name;
  }

  /** One run of a test. Skipped runs count for nothing but are kept as outcomes. A test seen twice keeps its longest time. */
  public void add(String test, long ms, Outcome outcome) {
    outcomes.computeIfAbsent(test, k -> EnumSet.noneOf(Outcome.class)).add(outcome);
    if (outcome != Outcome.SKIPPED) {
      durations.merge(test, Math.max(0, ms), Math::max);
    }
  }

  public Map<String, Long> durations() {
    return Map.copyOf(durations);
  }

  public boolean hasDurations() {
    return !durations.isEmpty();
  }

  /** 95th percentile of the test durations in ms, nearest-rank method; 0 when there is none. */
  public long p95() {
    if (durations.isEmpty()) {
      return 0;
    }
    List<Long> sorted = durations.values().stream().sorted().toList();
    return sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
  }

  /** The {@code n} slowest tests, slowest first, ties by name. */
  public List<Map.Entry<String, Long>> slowest(int n) {
    List<Map.Entry<String, Long>> all = new ArrayList<>(durations.entrySet());
    all.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
    return all.subList(0, Math.min(n, all.size()));
  }

  /** {@code [{"name":..., "ms":...}]} of the 10 slowest tests. */
  public String slowestJson() {
    JsonArray out = new JsonArray();
    for (Map.Entry<String, Long> e : slowest(10)) {
      JsonObject o = new JsonObject();
      o.addProperty("name", e.getKey());
      o.addProperty("ms", e.getValue());
      out.add(o);
    }
    return out.toString();
  }

  /** Tests that both passed and failed. */
  public SortedSet<String> flaky() {
    SortedSet<String> out = new TreeSet<>();
    outcomes.forEach((test, o) -> {
      if (o.contains(Outcome.PASSED) && o.contains(Outcome.FAILED)) {
        out.add(test);
      }
    });
    return out;
  }
}
