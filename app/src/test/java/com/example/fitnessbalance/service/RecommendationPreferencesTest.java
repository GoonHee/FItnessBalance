package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.Values;
import java.util.*;
import org.junit.Test;

public class RecommendationPreferencesTest {
  private final TimeZone zone = TimeZone.getTimeZone("Asia/Kuala_Lumpur");

  private Date at(int day) {
    Calendar c = Calendar.getInstance(zone);
    c.set(2026, 9, day, 12, 0, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c.getTime();
  }

  private RecommendationService.Preferences from(Map<String, Object> profile) {
    return new RecommendationPreferences()
        .from(profile, Collections.emptyList(), null, null, at(28), zone);
  }

  @Test
  public void missingProfileUsesDefaults() {
    RecommendationService.Preferences p = from(Collections.emptyMap());
    assertEquals(10, p.minutes);
    assertFalse(p.durationPreferenceKnown);
    assertFalse(p.recoveryDay);
  }

  @Test
  public void firestoreNumbersMayBeDoubles() {
    RecommendationService.Preferences p = from(Values.of("preferredWorkoutMinutes", 5.0));
    assertEquals(5, p.minutes);
    assertTrue(p.durationPreferenceKnown);
  }

  @Test
  public void unscheduledDayIsRecovery() {
    assertTrue(from(Values.of("preferredWorkoutDays", Arrays.asList(1, 5))).recoveryDay);
  }

  @Test
  public void scheduledDayIsNotRecovery() {
    assertFalse(from(Values.of("preferredWorkoutDays", Arrays.asList(3.0))).recoveryDay);
  }

  @Test
  public void preferredExercisesMapToCategories() {
    RecommendationService.Preferences p =
        from(Values.of("preferredExercises", Arrays.asList("Yoga", "Weight Lifting", "Walking")));
    assertTrue(p.categories.containsAll(Arrays.asList("mobility", "strength", "cardio")));
  }

  @Test
  public void discomfortUsesRecentCalendarDays() {
    RecommendationPreferences f = new RecommendationPreferences();
    assertTrue(
        f.from(Collections.emptyMap(), Collections.emptyList(), null, at(21), at(28), zone)
            .discomfort);
    assertFalse(
        f.from(Collections.emptyMap(), Collections.emptyList(), null, at(20), at(28), zone)
            .discomfort);
  }

  @Test
  public void weekOfInactivityIsRecognized() {
    RecommendationService.Preferences p =
        new RecommendationPreferences()
            .from(Collections.emptyMap(), Collections.emptyList(), at(21), null, at(28), zone);
    assertEquals(7, p.inactiveDays);
  }

  @Test
  public void onlyRecentQualifyingStrengthCounts() {
    List<Map<String, Object>> records =
        Arrays.asList(
            Values.of(
                "category",
                "strength",
                "completedDate",
                at(27),
                "completionPercentage",
                100,
                "difficultyFeedback",
                "suitable"),
            Values.of("category", "cardio", "completedDate", at(20), "completionPercentage", 100));
    RecommendationService.Preferences p =
        new RecommendationPreferences()
            .from(Collections.emptyMap(), records, null, null, at(28), zone);
    assertTrue(p.recentCategories.contains("strength"));
    assertFalse(p.recentCategories.contains("cardio"));
  }
}
