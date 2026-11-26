package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.Values;
import java.util.*;

/** Read-only milestones derived from saved logs. They do not modify existing badge or XP receipts. */
public final class AchievementProgress {
  public enum Metric {
    SESSIONS(5, 20, 50), ACTIVE_DAYS, ACTIVE_MINUTES(60, 300, 1000),
    TARGET_WEEKS(2, 4, 12), WORKOUT_CATEGORIES, EXERCISES(3, 8, 15),
    FEEDBACK(5, 15, 30), RECOVERY, ALL_ROUNDER, FOOD_DAYS, BALANCED_DAYS, PATH_LESSONS, COMEBACK_SESSIONS;

    private final int[] targets;

    Metric(int... targets) {
      this.targets = targets;
    }

    public int[] tierTargets() {
      return targets.clone();
    }
  }

  public static boolean earned(int value, int target) {
    return target > 0 && value >= target;
  }

  public static Map<Metric, Integer> calculate(List<Map<String, Object>> guided,
      List<Map<String, Object>> wearable, List<Map<String, Object>> weeks,
      Map<String, Map<String, Object>> food, Collection<String> legacyGoalWeeks) {
    EnumMap<Metric, Integer> result = new EnumMap<>(Metric.class);
    Set<String> days = new HashSet<>(), exercises = new HashSet<>(), categories = new HashSet<>();
    Map<String, Set<String>> weeklyCategories = new HashMap<>();
    Set<String> pathLessons = new HashSet<>();
    List<String> creditedDays = new ArrayList<>();
    String comeback = null;
    int sessions = 0, feedback = 0, recovery = 0;
    Map<String, Integer> activeByDay = new HashMap<>();
    for (Map<String, Object> row : guided) {
      int active = Values.integer(row.get("activeDurationSeconds"), 0);
      if (active >= GamificationService.MIN_GUIDED_ACTIVE_SECONDS
          && Values.integer(row.get("xpEarned"), 0) > 0
          && !Values.text(row.get("difficultyFeedback"), "").isEmpty()) feedback++;
      if (!DailyChallengeService.eligible(row)) continue;
      sessions++;
      String lesson = Values.text(row.get("lessonId"), "");
      if ("beginner_foundation".equals(row.get("pathId")) && lesson.matches("lesson_0[1-7]"))
        pathLessons.add(lesson);
      String day = Values.text(row.get("localDay"), "");
      if (!day.isEmpty()) {
        creditedDays.add(day);
        if (Values.strings(row.get("unlocked")).contains("welcome_back")
            && (comeback == null || day.compareTo(comeback) < 0)) comeback = day;
      }
      if (!day.isEmpty()) activeByDay.merge(day, Math.min(active, 1800), (a, b) -> Math.min(a + b, 1800));
      for (String id : Values.strings(row.get("completedExerciseIds")))
        if (!id.isEmpty()) exercises.add(id);
      addActivity(row.get("localDay"), row.get("localWeek"), row.get("category"),
          days, categories, weeklyCategories);
      if (Arrays.asList("mobility", "recovery").contains(row.get("category"))) recovery++;
    }
    for (Map<String, Object> row : wearable) {
      if (!Boolean.TRUE.equals(row.get("newDayCredit"))) continue;
      sessions++;
      String day = Values.text(row.get("day"), "");
      if (!day.isEmpty()) {
        creditedDays.add(day);
        if (Values.strings(row.get("unlocked")).contains("welcome_back")
            && (comeback == null || day.compareTo(comeback) < 0)) comeback = day;
      }
      addActivity(row.get("day"), row.get("week"), row.get("category"),
          days, categories, weeklyCategories);
      if ("mobility".equals(row.get("category"))) recovery++;
    }
    Set<String> goalWeeks = new HashSet<>(legacyGoalWeeks);
    for (Map<String, Object> row : weeks) {
      if (Boolean.TRUE.equals(row.get("bonusAwarded"))) {
        String week = Values.text(row.get("id"), "");
        if (!week.isEmpty()) goalWeeks.add(week);
      }
    }
    Set<String> mealDays = new HashSet<>();
    for (Map.Entry<String, Map<String, Object>> entry : food.entrySet()) {
      Map<String, Object> day = entry.getValue();
      Object items = day.get("foodItems");
      if (items instanceof List && !((List<?>) items).isEmpty()
          || !day.containsKey("foodItems") && Values.decimal(day.get("totalCalories"), 0) > 0)
        mealDays.add(entry.getKey());
    }
    int foodDays = mealDays.size();
    mealDays.retainAll(days);
    result.put(Metric.SESSIONS, sessions);
    result.put(Metric.ACTIVE_DAYS, days.size());
    long seconds = activeByDay.values().stream().mapToLong(Integer::longValue).sum();
    result.put(Metric.ACTIVE_MINUTES, (int) Math.min(Integer.MAX_VALUE, seconds / 60));
    result.put(Metric.TARGET_WEEKS, goalWeeks.size());
    result.put(Metric.WORKOUT_CATEGORIES, categories.size());
    result.put(Metric.EXERCISES, exercises.size());
    result.put(Metric.FEEDBACK, feedback);
    result.put(Metric.RECOVERY, recovery);
    result.put(Metric.ALL_ROUNDER, (int) weeklyCategories.values().stream()
        .filter(set -> set.containsAll(Arrays.asList("cardio", "strength", "mobility"))).count());
    result.put(Metric.FOOD_DAYS, foodDays);
    result.put(Metric.BALANCED_DAYS, mealDays.size());
    result.put(Metric.PATH_LESSONS, pathLessons.size());
    int sinceComeback = 0;
    if (comeback != null)
      for (String day : creditedDays) if (day.compareTo(comeback) >= 0) sinceComeback++;
    result.put(Metric.COMEBACK_SESSIONS, sinceComeback);
    return result;
  }

  private static void addActivity(Object day, Object week, Object category, Set<String> days,
      Set<String> categories, Map<String, Set<String>> weeklyCategories) {
    if (day instanceof String && !((String) day).isEmpty()) days.add((String) day);
    if (!(category instanceof String) || !Arrays.asList("strength", "cardio", "mobility", "balance", "recovery").contains(category)) return;
    categories.add((String) category);
    if (week instanceof String && !((String) week).isEmpty())
      weeklyCategories.computeIfAbsent((String) week, key -> new HashSet<>()).add((String) category);
  }
}
