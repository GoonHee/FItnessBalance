package com.example.fitnessbalance.service;

import static org.junit.Assert.*;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

public class DailyChallengeServiceTest {
  private final LocalDate today = LocalDate.of(2026, 11, 25);
  private final DailyChallengeService service = new DailyChallengeService();
  private final RecommendationService.Preferences prefs = new RecommendationService.Preferences();

  private WorkoutLesson lesson(String id, String slot, String category, String gear) {
    WorkoutLesson lesson = new WorkoutLesson();
    lesson.setId(id);
    lesson.setDailyChallenge(slot);
    lesson.setActive(true);
    lesson.setStandalone(true);
    lesson.setEstimatedMinutes(3);
    lesson.setDifficulty("beginner");
    lesson.setCategory(category);
    lesson.setEquipment(Collections.singletonList(gear));
    return lesson;
  }

  @Test public void equivalentChoicesRotateAndAvoidYesterday() {
    WorkoutLesson a = lesson("a", "quick", "cardio", "none");
    WorkoutLesson b = lesson("b", "quick", "balance", "none");
    List<WorkoutLesson> choices = Arrays.asList(a, b);
    WorkoutLesson first = service.select("quick", today, prefs, choices, Collections.emptyList()).lesson;
    assertNotEquals(first, service.select("quick", today.plusDays(1), prefs, choices, Collections.emptyList()).lesson);
    Map<String, Object> previous = Values.of("lessonId", first.getId(), "localDay", today.minusDays(1).toString());
    assertNotEquals(first, service.select("quick", today, prefs, choices, Collections.singletonList(previous)).lesson);
  }

  @Test public void usesDifferentExercisesWhenThePreviousLessonSharesTheirMovements() {
    WorkoutLesson repeating = lesson("similar", "quick", "cardio", "none");
    repeating.setExerciseIds(Arrays.asList("march", "stretch"));
    WorkoutLesson varied = lesson("varied", "quick", "balance", "none");
    varied.setExerciseIds(Collections.singletonList("balance"));
    Map<String, Object> previous = Values.of("lessonId", "different_lesson",
        "localDay", today.minusDays(1).toString(),
        "completedExerciseIds", Collections.singletonList("march"));
    assertSame(varied, service.select("quick", today, prefs, Arrays.asList(repeating, varied),
        Collections.singletonList(previous)).lesson);
  }

  @Test public void rotatesAwayFromTwoRecentWorkoutsInTheSameCategory() {
    WorkoutLesson cardio = lesson("cardio", "quick", "cardio", "none");
    WorkoutLesson balance = lesson("balance", "quick", "balance", "none");
    prefs.rotationCategories.addAll(Arrays.asList("cardio", "cardio"));
    assertSame(balance, service.select("quick", today, prefs, Arrays.asList(cardio, balance),
        Collections.emptyList()).lesson);
  }

  @Test public void respectsEquipmentAndRecovery() {
    WorkoutLesson chair = lesson("chair", "quick", "strength", "chair");
    WorkoutLesson gentle = lesson("walk", "wellness", "recovery", "none");
    assertNull(service.select("quick", today, prefs, Arrays.asList(chair, gentle), Collections.emptyList()).lesson);
    prefs.equipment.add("chair");
    prefs.tired = true;
    assertNull(service.select("quick", today, prefs, Arrays.asList(chair, gentle), Collections.emptyList()).lesson);
    assertSame(gentle, service.select("wellness", today, prefs, Arrays.asList(chair, gentle), Collections.emptyList()).lesson);
    prefs.discomfort = true;
    assertNull(service.select("wellness", today, prefs, Arrays.asList(chair, gentle), Collections.emptyList()).lesson);
  }

  @Test public void savedQualifyingSessionCompletesItsSlotOnlyOnThatDate() {
    WorkoutLesson a = lesson("a", "quick", "cardio", "none");
    Map<String, Object> record = Values.of("lessonId", "a", "localDay", today.toString(),
        "completionPercentage", 100, "activeDurationSeconds", 65, "xpEarned", 5);
    assertTrue(service.select("quick", today, prefs, Collections.singletonList(a), Collections.singletonList(record)).completed);
    assertFalse(service.select("quick", today.plusDays(1), prefs, Collections.singletonList(a), Collections.singletonList(record)).completed);
    record.put("xpEarned", 0);
    assertFalse(service.select("quick", today, prefs, Collections.singletonList(a), Collections.singletonList(record)).completed);
  }
}
