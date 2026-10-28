package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.*;
import java.util.*;

/** Profile/record mapping remains independent of Android and Firestore APIs. */
public final class RecommendationContextFactory {
  public RecommendationService.Context create(
      Map<String, Object> profile,
      List<Map<String, Object>> recent,
      Date now,
      TimeZone zone,
      Date lastWorkout,
      Date lastDiscomfort) {
    RecommendationService.Context c = new RecommendationService.Context();
    c.inactiveDays = DateUtils.daysSince(lastWorkout, now, zone);
    c.discomfort = lastDiscomfort != null && DateUtils.daysSince(lastDiscomfort, now, zone) <= 7;
    c.preferredMinutes = Values.integer(profile.get("preferredWorkoutMinutes"), 10);
    c.fitnessLevel = Values.text(profile.get("fitnessLevel"), "beginner").toLowerCase(Locale.ROOT);
    c.goal = Values.text(profile.get("fitnessGoal"), "");
    c.equipment = Values.strings(profile.get("equipment"));
    c.preferredCategories = Values.strings(profile.get("preferredExerciseCategories"));
    for (String p : Values.strings(profile.get("preferredExercises"))) {
      String s = p.toLowerCase(Locale.ROOT);
      if (s.contains("yoga") || s.contains("pilates")) c.preferredCategories.add("mobility");
      else if (s.contains("weight")) c.preferredCategories.add("strength");
      else if (s.contains("running")
          || s.contains("cycling")
          || s.contains("walking")
          || s.contains("swimming")) c.preferredCategories.add("cardio");
    }
    Calendar cal = Calendar.getInstance(zone);
    cal.setTime(now);
    int iso = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    Object days = profile.get("preferredWorkoutDays");
    if (days instanceof List && !((List<?>) days).isEmpty()) {
      boolean planned = false;
      for (Object day : (List<?>) days) if (Values.integer(day, -1) == iso) planned = true;
      c.recoveryDay = !planned;
    }
    for (Map<String, Object> record : recent) {
      String difficulty = Values.text(record.get("difficultyFeedback"), "");
      if (c.recentDifficulties.size() < 2) c.recentDifficulties.add(difficulty);
      if (c.previousDifficulty.isEmpty()) c.previousDifficulty = difficulty;
      Object completedAt = record.get("completedDate");
      if (completedAt instanceof Date
          && now.getTime() - ((Date) completedAt).getTime() <= 48L * 3600 * 1000
          && Values.decimal(record.get("completionPercentage"), 0) >= 50)
        c.recentCategories.add(Values.text(record.get("category"), ""));
    }
    return c;
  }
}
