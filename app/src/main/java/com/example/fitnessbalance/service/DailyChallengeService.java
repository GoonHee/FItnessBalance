package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.time.LocalDate;
import java.util.*;

/** Chooses a short catalogue lesson, with safety and available equipment as hard filters. */
public final class DailyChallengeService {
  public static final class Challenge {
    public final WorkoutLesson lesson;
    public final boolean completed;

    Challenge(WorkoutLesson lesson, boolean completed) {
      this.lesson = lesson;
      this.completed = completed;
    }
  }

  public Challenge select(String slot, LocalDate day, RecommendationService.Preferences prefs,
      List<WorkoutLesson> catalogue, List<Map<String, Object>> recent) {
    Map<String, WorkoutLesson> lessons = new HashMap<>();
    for (WorkoutLesson lesson : catalogue)
      if (slot.equals(lesson.getDailyChallenge())) lessons.put(lesson.getId(), lesson);
    for (Map<String, Object> record : recent) {
      WorkoutLesson lesson = lessons.get(Values.text(record.get("lessonId"), ""));
      if (day.toString().equals(record.get("localDay")) && lesson != null && eligible(record))
        return new Challenge(lesson, true);
    }
    if (prefs.discomfort || ("quick".equals(slot) && (prefs.recoveryDay || prefs.tired
        || (!prefs.lastDifficulties.isEmpty() && "too_difficult".equals(prefs.lastDifficulties.get(0))))))
      return new Challenge(null, false);

    List<WorkoutLesson> available = new ArrayList<>();
    for (WorkoutLesson lesson : lessons.values()) {
      if (!lesson.getActive() || !lesson.getStandalone() || lesson.getEstimatedMinutes() < 2
          || lesson.getEstimatedMinutes() > 5 || !("beginner".equalsIgnoreCase(lesson.getDifficulty())
              || prefs.fitnessLevel.equalsIgnoreCase(lesson.getDifficulty()))
          || !prefs.equipment.containsAll(requiredGear(lesson))) continue;
      available.add(lesson);
    }
    if (available.isEmpty()) return new Challenge(null, false);
    String yesterday = day.minusDays(1).toString();
    Set<String> yesterdayLessons = new HashSet<>(), yesterdayExercises = new HashSet<>();
    for (Map<String, Object> record : recent) {
      if (!yesterday.equals(record.get("localDay"))) continue;
      yesterdayLessons.add(Values.text(record.get("lessonId"), ""));
      yesterdayExercises.addAll(Values.strings(record.get("completedExerciseIds")));
    }
    if (available.stream().anyMatch(lesson -> !yesterdayLessons.contains(lesson.getId())))
      available.removeIf(lesson -> yesterdayLessons.contains(lesson.getId()));
    if (available.stream().anyMatch(lesson -> Collections.disjoint(lesson.getExerciseIds(), yesterdayExercises)))
      available.removeIf(lesson -> !Collections.disjoint(lesson.getExerciseIds(), yesterdayExercises));
    if (available.stream().anyMatch(lesson -> Collections.frequency(prefs.rotationCategories, lesson.getCategory()) < 2))
      available.removeIf(lesson -> Collections.frequency(prefs.rotationCategories, lesson.getCategory()) >= 2);

    available.sort(Comparator.comparingInt((WorkoutLesson lesson) ->
        (lesson.getCategory().equals(prefs.lastCategory) ? 8 : 0)
            - (prefs.categories.contains(lesson.getCategory())
                || prefs.goal.toLowerCase(Locale.ROOT).contains(lesson.getCategory()) ? 5 : 0))
        .thenComparing(WorkoutLesson::getId));
    int index = (int) Math.floorMod(day.toEpochDay(), (long) available.size());
    return new Challenge(available.get(index), false);
  }

  public static boolean eligible(Map<String, Object> record) {
    return Values.decimal(record.get("completionPercentage"), 0) >= 50
        && Values.integer(record.get("activeDurationSeconds"), 0) >= GamificationService.MIN_GUIDED_ACTIVE_SECONDS
        && Values.integer(record.get("xpEarned"), 0) > 0;
  }

  private static List<String> requiredGear(WorkoutLesson lesson) {
    List<String> gear = new ArrayList<>(lesson.getEquipment());
    gear.remove("none");
    return gear;
  }
}
