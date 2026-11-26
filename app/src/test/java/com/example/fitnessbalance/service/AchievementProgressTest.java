package com.example.fitnessbalance.service;

import static com.example.fitnessbalance.service.AchievementProgress.Metric.*;
import static org.junit.Assert.*;
import com.example.fitnessbalance.util.Values;
import java.util.*;
import org.junit.Test;

public class AchievementProgressTest {
  @Test public void tiersUnlockAtTheirExactThresholds() {
    assertArrayEquals(new int[]{5, 20, 50}, SESSIONS.tierTargets());
    assertFalse(AchievementProgress.earned(19, SESSIONS.tierTargets()[1]));
    assertTrue(AchievementProgress.earned(20, SESSIONS.tierTargets()[1]));
    assertFalse(AchievementProgress.earned(20, SESSIONS.tierTargets()[2]));
  }

  @Test public void progressDeduplicatesDatesAndExercisesAndCapsLongSessions() {
    String day = "2026-11-25", week = "2026-11-23";
    Map<String, Object> saved = Values.of("localDay", day, "localWeek", week, "category", "cardio",
        "completionPercentage", 100, "activeDurationSeconds", 7200, "xpEarned", 10,
        "difficultyFeedback", "suitable", "completedExerciseIds", Arrays.asList("EX001", "EX002"));
    Map<String, Object> legacy = Values.of("localDay", day, "localWeek", week, "category", "strength",
        "completionPercentage", 100, "activeDurationSeconds", 120, "xpEarned", 5);
    Map<String, Object> rejected = Values.of("localDay", day, "category", "recovery",
        "completionPercentage", 100, "activeDurationSeconds", 400, "xpEarned", 0,
        "completedExerciseIds", Collections.singletonList("EX003"));
    Map<String, Object> wearable = Values.of("day", day, "week", week, "category", "mobility", "newDayCredit", true);
    Map<AchievementProgress.Metric, Integer> progress = AchievementProgress.calculate(
        Arrays.asList(saved, legacy, rejected), Collections.singletonList(wearable),
        Collections.singletonList(Values.of("id", week, "bonusAwarded", true)),
        Collections.singletonMap(day, Values.of("foodItems", Collections.singletonList("meal"))),
        Collections.singletonList(week));
    assertEquals(3, progress.get(SESSIONS).intValue());
    assertEquals(1, progress.get(ACTIVE_DAYS).intValue());
    assertEquals(30, progress.get(ACTIVE_MINUTES).intValue());
    assertEquals(2, progress.get(EXERCISES).intValue());
    assertEquals(1, progress.get(ALL_ROUNDER).intValue());
    assertEquals(1, progress.get(TARGET_WEEKS).intValue());
    assertEquals(1, progress.get(BALANCED_DAYS).intValue());
    assertEquals(1, progress.get(FEEDBACK).intValue());
  }
}
