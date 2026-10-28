package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.Values;
import java.util.*;
import org.junit.Test;

public class ExerciseLogServiceTest {
  private final ExerciseLogService service = new ExerciseLogService();

  private Map<String, Object> row(String name, Number min, Number cal, boolean done) {
    return Values.of("name", name, "duration", min, "calories", cal, "completed", done);
  }

  private long total(Map<String, Object> m, String key) {
    return ((Number) m.get(key)).longValue();
  }

  @Test
  public void estimatesMatchGuidedMetFormula() {
    assertEquals(129, new ExerciseCalorieService().estimate(3.5, 70, 30));
  }

  @Test
  public void invalidWeightUsesDocumentedReference() {
    assertEquals(70, new ExerciseCalorieService().weight(Double.NaN), 0);
    assertEquals(70, new ExerciseCalorieService().weight(0), 0);
    assertEquals(62, new ExerciseCalorieService().weight(62), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidDurationRejected() {
    new ExerciseCalorieService().estimate(3, 70, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidIntensityRejected() {
    new ExerciseCalorieService().estimate(Double.NaN, 70, 30);
  }

  @Test
  public void legacyIdsAreStableAndDuplicatesStayDistinct() {
    List<Map<String, Object>> rows =
        Arrays.asList(row("Walking", 30L, 120L, true), row("Walking", 30L, 120L, true));
    Map<String, Object> doc = Values.of("exercises", rows);
    List<Map<String, Object>> a = service.entries("2026-10-28", doc),
        b = service.entries("2026-10-28", doc);
    assertEquals(a.get(0).get("entryId"), b.get(0).get("entryId"));
    assertNotEquals(a.get(0).get("entryId"), a.get(1).get("entryId"));
    assertFalse(rows.get(0).containsKey("entryId"));
  }

  @Test
  public void existingFieldsAndCaloriesArePreserved() {
    Map<String, Object> old = row("Walking", 30.0, 115.5, true);
    old.put("customField", "retained");
    Map<String, Object> mapped =
        service.entries("2026-10-28", Values.of("exercises", Arrays.asList(old))).get(0);
    assertEquals(115.5, (Double) mapped.get("calories"), 0);
    assertEquals("retained", mapped.get("customField"));
  }

  @Test
  public void plannedActivitiesDontCount() {
    Map<String, Object> m =
        service.totals(
            Arrays.asList(row("Walk", 30, 100, false), row("Run", 10, 75, true)),
            Collections.emptyMap());
    assertEquals(75, total(m, "caloriesBurned"));
    assertEquals(600, total(m, "exerciseActiveSeconds"));
    assertEquals(1, total(m, "exerciseEntryCount"));
  }

  @Test
  public void manualAndGuidedTotalsCombine() {
    Map<String, Object> guided = new HashMap<>();
    service.guided(
        guided,
        "session",
        Values.of("estimatedCaloriesBurned", 25L, "activeDurationSeconds", 180L));
    Map<String, Object> m = service.totals(Arrays.asList(row("Walking", 30.0, 120L, true)), guided);
    assertEquals(145, total(m, "caloriesBurned"));
    assertEquals(120, total(m, "manualCaloriesBurned"));
    assertEquals(25, total(m, "structuredCaloriesBurned"));
    assertEquals(1980, total(m, "exerciseActiveSeconds"));
  }

  @Test
  public void guidedReconciliationIsIdempotent() {
    Map<String, Object> guided = new HashMap<>();
    Map<String, Object> record =
        Values.of("estimatedCaloriesBurned", 25, "activeDurationSeconds", 180);
    service.guided(guided, "session", record);
    service.guided(guided, "session", record);
    assertEquals(25, total(service.totals(Collections.emptyList(), guided), "caloriesBurned"));
  }

  @Test
  public void reconciliationPreservesSimultaneousNewRecords() {
    Map<String, Object> guided = new HashMap<>();
    service.guided(guided, "new", Values.of("estimatedCaloriesBurned", 40));
    service.guided(guided, "old", Values.of("estimatedCaloriesBurned", 25));
    assertEquals(65, total(service.totals(Collections.emptyList(), guided), "caloriesBurned"));
  }

  @Test
  public void addRetryDoesNotDuplicate() {
    Map<String, Object> item = row("Walking", 30, 120, true);
    List<Map<String, Object>> first =
        service.mutate(Collections.emptyList(), "entry", 0, item, true, false);
    List<Map<String, Object>> retried = service.mutate(first, "entry", 0, item, true, false);
    assertEquals(1, retried.size());
  }

  @Test
  public void editsKeepUnknownFields() {
    Map<String, Object> item = row("Walk", 30, 120, true);
    item.put("custom", "keep");
    List<Map<String, Object>> first =
        service.mutate(Collections.emptyList(), "entry", 0, item, true, false);
    List<Map<String, Object>> edited =
        service.mutate(first, "entry", 1, row("Walk", 15, 60, true), false, false);
    assertEquals("keep", edited.get(0).get("custom"));
    assertEquals(60, total(service.totals(edited, Collections.emptyMap()), "caloriesBurned"));
  }

  @Test(expected = ExerciseLogService.Conflict.class)
  public void staleEditDoesNotOverwriteAnotherEdit() {
    List<Map<String, Object>> first =
        service.mutate(
            Collections.emptyList(), "entry", 0, row("Walk", 30, 120, true), true, false);
    service.mutate(first, "entry", 0, row("Walk", 10, 40, true), false, false);
  }

  @Test
  public void confirmedEditRetryIsIdempotent() {
    List<Map<String, Object>> first =
        service.mutate(
            Collections.emptyList(), "entry", 0, row("Walk", 30, 120, true), true, false);
    Map<String, Object> edit = row("Walk", 15, 60, true);
    edit.put("lastMutationId", "operation");
    List<Map<String, Object>> second = service.mutate(first, "entry", 1, edit, false, false);
    List<Map<String, Object>> retry = service.mutate(second, "entry", 1, edit, false, false);
    assertEquals(2, retry.get(0).get("revision"));
    assertEquals(1, retry.size());
  }

  @Test
  public void deletionRetryPreservesGuidedTotals() {
    List<Map<String, Object>> first =
        service.mutate(
            Collections.emptyList(), "entry", 0, row("Walk", 30, 120, true), true, false);
    List<Map<String, Object>> removed = service.mutate(first, "entry", 1, null, false, true);
    removed = service.mutate(removed, "entry", 1, null, false, true);
    Map<String, Object> guided = new HashMap<>();
    service.guided(guided, "session", Values.of("estimatedCaloriesBurned", 25));
    assertEquals(25, total(service.totals(removed, guided), "caloriesBurned"));
  }

  @Test
  public void aggregateOnlyLegacyDayIsPreserved() {
    List<Map<String, Object>> entries =
        service.entries("2026-10-28", Values.of("totalCaloriesBurned", 90L));
    assertEquals(90, total(service.totals(entries, Collections.emptyMap()), "caloriesBurned"));
    assertEquals(
        0, total(service.totals(entries, Collections.emptyMap()), "exerciseActiveSeconds"));
  }

  @Test
  public void dashboardOnlyLegacyTotalIsPreserved() {
    List<Map<String, Object>> rows =
        service.manualEntries(
            "2026-10-28", Collections.emptyMap(), Values.of("caloriesBurned", 90L));
    assertEquals(90, total(service.totals(rows, Collections.emptyMap()), "manualCaloriesBurned"));
  }

  @Test
  public void versionTwoGuidedTotalDoesNotBecomeManual() {
    List<Map<String, Object>> rows =
        service.manualEntries(
            "2026-10-28",
            Collections.emptyMap(),
            Values.of("caloriesBurned", 25, "exerciseTotalsVersion", 2));
    assertTrue(rows.isEmpty());
  }

  @Test(expected = IllegalStateException.class)
  public void corruptRowsCannotBeSilentlyErased() {
    service.entries("2026-10-28", Values.of("exercises", "bad"));
  }

  @Test(expected = IllegalStateException.class)
  public void negativeCalorieDataIsRejected() {
    service.totals(Arrays.asList(row("Walk", 30, -5, true)), Collections.emptyMap());
  }

  @Test
  public void missingActiveDurationDoesNotInventMovement() {
    Map<String, Object> guided = new HashMap<>();
    service.guided(guided, "old", Values.of("estimatedCaloriesBurned", 25, "durationSeconds", 300));
    assertEquals(
        0, total(service.totals(Collections.emptyList(), guided), "exerciseActiveSeconds"));
  }

  @Test(expected = ExerciseLogService.Conflict.class)
  public void addRetryCannotSilentlyIgnoreChangedFields() {
    Map<String, Object> entry = row("Walk", 30, 120, true);
    List<Map<String, Object>> first =
        service.mutate(Collections.emptyList(), "entry", 0, entry, true, false);
    service.mutate(first, "entry", 0, row("Walk", 15, 60, true), true, false);
  }

  @Test(expected = ExerciseLogService.Conflict.class)
  public void editedPayloadWithSameMutationIdIsNotAnIdempotentRetry() {
    List<Map<String, Object>> first =
        service.mutate(
            Collections.emptyList(), "entry", 0, row("Walk", 30, 120, true), true, false);
    Map<String, Object> edit = row("Walk", 15, 60, true);
    edit.put("lastMutationId", "operation");
    List<Map<String, Object>> second = service.mutate(first, "entry", 1, edit, false, false);
    Map<String, Object> changed = row("Walk", 10, 40, true);
    changed.put("lastMutationId", "operation");
    service.mutate(second, "entry", 1, changed, false, false);
  }

  @Test
  public void legacyDashboardBaselineSurvivesRepeatedRefreshes() {
    List<Map<String, Object>> first =
        service.manualEntries(
            "2026-10-28", Collections.emptyMap(), Values.of("caloriesBurned", 90));
    Map<String, Object> saved = service.totals(first, Collections.emptyMap());
    saved.put("structuredExerciseContributions", Collections.emptyMap());
    List<Map<String, Object>> again =
        service.manualEntries("2026-10-28", Collections.emptyMap(), saved);
    assertEquals(90, total(service.totals(again, Collections.emptyMap()), "caloriesBurned"));
  }

  @Test
  public void legacyBaselineSurvivesAGuidedCreditAndAnotherRefresh() {
    Map<String, Object> guided = new HashMap<>();
    service.guided(guided, "session", Values.of("estimatedCaloriesBurned", 25));
    Map<String, Object> daily =
        Values.of(
            "manualCaloriesBurned",
            90,
            "structuredExerciseContributions",
            guided,
            "exerciseTotalsVersion",
            2,
            "caloriesBurned",
            115);
    List<Map<String, Object>> entries =
        service.manualEntries("2026-10-28", Collections.emptyMap(), daily);
    assertEquals(115, total(service.totals(entries, guided), "caloriesBurned"));
  }
}
