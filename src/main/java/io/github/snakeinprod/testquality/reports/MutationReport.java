package io.github.snakeinprod.testquality.reports;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads a mutation-testing-elements JSON report (schemaVersion 1 and 2) into mutant counts per file path. */
public final class MutationReport {

  /** Counts of one file. {@code mutants} leaves out Ignored, Pending, CompileError and RuntimeError; killed includes Timeout. */
  public record Counts(int mutants, int killed, int survived, int noCoverage, int timeout) {
    public Counts plus(Counts o) {
      return new Counts(mutants + o.mutants, killed + o.killed, survived + o.survived, noCoverage + o.noCoverage, timeout + o.timeout);
    }
  }

  private MutationReport() {
  }

  /** File path as written in the report (absolute or relative) to its counts. */
  public static Map<String, Counts> parse(Reader json) {
    JsonElement root = JsonParser.parseReader(json);
    JsonElement files = root.isJsonObject() ? root.getAsJsonObject().get("files") : null;
    if (files == null || !files.isJsonObject()) {
      throw new IllegalArgumentException("not a mutation-testing-elements report: no \"files\" object");
    }
    Map<String, Counts> out = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> file : files.getAsJsonObject().entrySet()) {
      int mutants = 0;
      int killed = 0;
      int survived = 0;
      int noCoverage = 0;
      int timeout = 0;
      JsonElement list = file.getValue().isJsonObject() ? file.getValue().getAsJsonObject().get("mutants") : null;
      if (list != null && list.isJsonArray()) {
        for (JsonElement m : list.getAsJsonArray()) {
          JsonElement status = m.isJsonObject() ? ((JsonObject) m).get("status") : null;
          switch (status == null || !status.isJsonPrimitive() ? "" : status.getAsString()) {
            case "Killed" -> killed++;
            case "Timeout" -> {
              killed++;
              timeout++;
            }
            case "Survived" -> survived++;
            case "NoCoverage" -> noCoverage++;
            default -> {
              continue; // Ignored, Pending, CompileError, RuntimeError, unknown
            }
          }
          mutants++;
        }
      }
      out.merge(file.getKey(), new Counts(mutants, killed, survived, noCoverage, timeout), Counts::plus);
    }
    return out;
  }
}
