package io.github.snakeinprod.testquality.reports;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Reads the JSON lines of {@code flutter test --machine} / {@code dart test --reporter json} into {@link Results}. */
public final class DartTestReport {

  private record Started(String name, String suiteId, String url, long time) {
  }

  private DartTestReport() {
  }

  /** Adds every finished, not hidden test to {@code into}. Lines that are not JSON events are ignored. */
  public static void parse(Reader lines, Results into) throws IOException {
    Map<String, String> suites = new HashMap<>();
    Map<String, Started> started = new HashMap<>();
    BufferedReader in = new BufferedReader(lines);
    for (String line = in.readLine(); line != null; line = in.readLine()) {
      JsonObject e = event(line);
      if (e == null) {
        continue;
      }
      switch (str(e, "type")) {
        case "suite" -> {
          JsonObject s = obj(e, "suite");
          if (s != null) {
            suites.put(str(s, "id"), str(s, "path"));
          }
        }
        case "testStart" -> {
          JsonObject t = obj(e, "test");
          if (t != null) {
            started.put(str(t, "id"), new Started(str(t, "name"), str(t, "suiteID"), str(t, "url"), num(e, "time")));
          }
        }
        case "testDone" -> {
          Started s = started.remove(str(e, "testID"));
          if (s == null || bool(e, "hidden")) {
            continue; // loading tests and setUpAll/tearDownAll are hidden
          }
          String path = suites.getOrDefault(s.suiteId(), "");
          String prefix = path.isEmpty() ? s.url() : path;
          Results.Outcome outcome = bool(e, "skipped") ? Results.Outcome.SKIPPED
            : "success".equals(str(e, "result")) ? Results.Outcome.PASSED : Results.Outcome.FAILED;
          into.add(Results.id(prefix, s.name()), num(e, "time") - s.time(), outcome);
        }
        default -> {
          // print, group, allSuites, done, error...
        }
      }
    }
  }

  private static JsonObject event(String line) {
    String l = line.trim();
    if (!l.startsWith("{")) {
      return null;
    }
    try {
      JsonElement e = JsonParser.parseString(l);
      return e.isJsonObject() ? e.getAsJsonObject() : null;
    } catch (JsonParseException ex) {
      return null;
    }
  }

  private static JsonObject obj(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
  }

  private static String str(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
  }

  private static long num(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
  }

  private static boolean bool(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
  }
}
