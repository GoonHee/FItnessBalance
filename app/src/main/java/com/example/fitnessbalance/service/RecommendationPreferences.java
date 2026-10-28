package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.*;
import java.util.*;

public final class RecommendationPreferences {
  public RecommendationService.Preferences from(
      Map<String, Object> profile,
      List<Map<String, Object>> recent,
      Date lastWorkout,
      Date lastDiscomfort,
      Date now,
      TimeZone zone) {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.minutes = Values.integer(profile.get("preferredWorkoutMinutes"), 10);
    p.durationPreferenceKnown = profile.get("preferredWorkoutMinutes") instanceof Number;
    p.fitnessLevel = Values.text(profile.get("fitnessLevel"), "beginner");
    p.goal = Values.text(profile.get("fitnessGoal"), "");
    p.categories = Values.strings(profile.get("preferredExerciseCategories"));
    p.equipment = Values.strings(profile.get("equipment"));
    p.inactiveDays = DateUtils.daysBetween(lastWorkout, now, zone);
    p.discomfort = lastDiscomfort != null && DateUtils.daysBetween(lastDiscomfort, now, zone) <= 7;
    for (String preferred : Values.strings(profile.get("preferredExercises"))) {
      String lower = preferred.toLowerCase(Locale.ROOT);
      if (lower.contains("yoga") || lower.contains("pilates")) p.categories.add("mobility");
      else if (lower.contains("weight")) p.categories.add("strength");
      else if (lower.contains("running")
          || lower.contains("cycling")
          || lower.contains("swimming")
          || lower.contains("walking")) p.categories.add("cardio");
    }
    Object days = profile.get("preferredWorkoutDays");
    if (days instanceof List && !((List<?>) days).isEmpty()) {
      boolean planned = false;
      for (Object d : (List<?>) days)
        if (Values.integer(d, -1) == DateUtils.isoDay(now, zone)) planned = true;
      p.recoveryDay = !planned;
    }
    for (Map<String, Object> r : recent) {
      if (p.lastDifficulties.size() < 2)
        p.lastDifficulties.add(Values.text(r.get("difficultyFeedback"), ""));
      Object time = r.get("completedDate");
      if (time instanceof Date
          && now.getTime() - ((Date) time).getTime() >= 0
          && now.getTime() - ((Date) time).getTime() <= 48L * 3600 * 1000
          && Values.decimal(r.get("completionPercentage"), 0) >= 50)
        p.recentCategories.add(Values.text(r.get("category"), ""));
    }
    return p;
  }
}
