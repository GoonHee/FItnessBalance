package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.Values;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure legacy mapping, idempotent mutations and source-aware daily accounting. */
public final class ExerciseLogService {
  public static final class Conflict extends IllegalStateException {
    public Conflict() {
      super("Activity changed; reload before editing");
    }
  }

  public List<Map<String, Object>> entries(String day, Map<String, Object> document) {
    List<Map<String, Object>> out = new ArrayList<>();
    Object raw = document.get("exercises");
    if (raw != null && !(raw instanceof List))
      throw new IllegalStateException("Invalid exercise log");
    if (raw instanceof List) {
      int n = 0;
      for (Object row : (List<?>) raw) {
        if (!(row instanceof Map)) throw new IllegalStateException("Invalid activity entry");
        Map<String, Object> item = Values.map(row);
        String id = Values.text(item.get("entryId"), "");
        if (id.isEmpty()) {
          String key =
              day
                  + "|"
                  + n
                  + "|"
                  + item.get("name")
                  + "|"
                  + item.get("duration")
                  + "|"
                  + item.get("calories")
                  + "|"
                  + item.get("completed");
          id = "legacy_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
          item.put("entryId", id);
        }
        out.add(item);
        n++;
      }
    }
    // Preserve a historical day that contains only its aggregate, without inventing duration.
    else if (number(document.get("totalCaloriesBurned"), 0) > 0)
      out.add(
          Values.of(
              "entryId",
              "legacy_total_" + day,
              "name",
              "",
              "duration",
              0,
              "calories",
              document.get("totalCaloriesBurned"),
              "completed",
              true,
              "legacySummary",
              true));
    return out;
  }

  public List<Map<String, Object>> manualEntries(
      String day, Map<String, Object> manual, Map<String, Object> daily) {
    if (manual.isEmpty() && number(daily.get("manualCaloriesBurned"), 0) > 0)
      return entries(day, Values.of("totalCaloriesBurned", daily.get("manualCaloriesBurned")));
    if (manual.isEmpty()
        && !daily.containsKey("exerciseTotalsVersion")
        && !daily.containsKey("structuredExerciseContributions")
        && number(daily.get("caloriesBurned"), 0) > 0)
      return entries(day, Values.of("totalCaloriesBurned", daily.get("caloriesBurned")));
    return entries(day, manual);
  }

  public List<Map<String, Object>> mutate(
      List<Map<String, Object>> entries,
      String id,
      int expectedRevision,
      Map<String, Object> replacement,
      boolean add,
      boolean remove) {
    List<Map<String, Object>> out = new ArrayList<>();
    int found = -1;
    for (Map<String, Object> row : entries) {
      out.add(new HashMap<>(row));
      if (id.equals(row.get("entryId"))) found = out.size() - 1;
    }
    if (add) {
      if (found >= 0) {
        if (!samePayload(out.get(found), replacement)) throw new Conflict();
        return out;
      }
      if (out.size() >= 1000) throw new IllegalStateException("Daily activity limit reached");
      Map<String, Object> item = new HashMap<>(replacement);
      item.put("entryId", id);
      item.put("revision", 1);
      out.add(item);
      return out;
    }
    if (found < 0) {
      if (remove) return out;
      throw new Conflict();
    }
    Map<String, Object> existing = out.get(found);
    if (!remove
        && replacement != null
        && replacement.get("lastMutationId") != null
        && replacement.get("lastMutationId").equals(existing.get("lastMutationId"))) {
      if (!samePayload(existing, replacement)) throw new Conflict();
      return out;
    }
    if (Values.integer(existing.get("revision"), 0) != expectedRevision) throw new Conflict();
    if (remove) out.remove(found);
    else {
      existing.putAll(replacement);
      existing.put("entryId", id);
      existing.put("revision", expectedRevision + 1);
    }
    return out;
  }

  public Map<String, Object> contributions(Object raw) {
    if (raw != null && !(raw instanceof Map))
      throw new IllegalStateException("Invalid daily exercise totals");
    return Values.map(raw);
  }

  public void guided(
      Map<String, Object> contributions, String recordId, Map<String, Object> record) {
    double calories = number(record.get("estimatedCaloriesBurned"), 0);
    double seconds = number(record.get("activeDurationSeconds"), 0);
    contributions.put(recordId, Values.of("calories", calories, "activeSeconds", seconds));
  }

  public Map<String, Object> totals(List<Map<String, Object>> manual, Map<String, Object> guided) {
    double manualCalories = 0, guidedCalories = 0, seconds = 0;
    int count = 0;
    for (Map<String, Object> row : manual)
      if (Boolean.TRUE.equals(row.get("completed"))) {
        manualCalories += number(row.get("calories"), 0);
        seconds += number(row.get("duration"), 0) * 60;
        count++;
      }
    for (Object raw : guided.values()) {
      if (!(raw instanceof Map)) throw new IllegalStateException("Invalid guided contribution");
      Map<String, Object> row = Values.map(raw);
      guidedCalories += number(row.get("calories"), 0);
      seconds += number(row.get("activeSeconds"), 0);
      count++;
    }
    if (manualCalories + guidedCalories > Integer.MAX_VALUE || seconds > Integer.MAX_VALUE)
      throw new IllegalStateException("Invalid daily exercise totals");
    return Values.of(
        "manualCaloriesBurned",
        Math.round(manualCalories),
        "structuredCaloriesBurned",
        Math.round(guidedCalories),
        "caloriesBurned",
        Math.round(manualCalories + guidedCalories),
        "exerciseActiveSeconds",
        Math.round(seconds),
        "exerciseEntryCount",
        count,
        "exerciseTotalsVersion",
        2);
  }

  private boolean samePayload(Map<String, Object> existing, Map<String, Object> replacement) {
    for (Map.Entry<String, Object> e : replacement.entrySet()) {
      Object a = existing.get(e.getKey()), b = e.getValue();
      if (a instanceof Number && b instanceof Number) {
        if (Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) != 0)
          return false;
      } else if (!Objects.equals(a, b)) return false;
    }
    return true;
  }

  private double number(Object value, double fallback) {
    if (value == null) return fallback;
    if (!(value instanceof Number))
      throw new IllegalStateException("Invalid numeric exercise value");
    double number = ((Number) value).doubleValue();
    if (Double.isNaN(number) || Double.isInfinite(number) || number < 0)
      throw new IllegalStateException("Invalid numeric exercise value");
    return number;
  }
}
