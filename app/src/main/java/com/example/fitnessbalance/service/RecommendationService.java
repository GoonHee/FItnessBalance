package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutLesson;
import java.util.*;

/** Rule-based recommendations, with safety taking precedence over progression. */
public final class RecommendationService {
  public static final class Preferences {
    public int inactiveDays, minutes = 10;
    public boolean discomfort, recoveryDay, durationPreferenceKnown;
    public String fitnessLevel = "beginner", goal = "";
    public List<String> equipment = new ArrayList<>(),
        categories = new ArrayList<>(),
        lastDifficulties = new ArrayList<>(),
        recentCategories = new ArrayList<>();
  }

  public static final class Recommendation {
    public final WorkoutLesson lesson;
    public final String reason;

    public Recommendation(WorkoutLesson lesson, String reason) {
      this.lesson = lesson;
      this.reason = reason;
    }
  }

  public Recommendation recommend(Preferences p, List<WorkoutLesson> available) {
    List<WorkoutLesson> choices = new ArrayList<>();
    for (WorkoutLesson l : available) if (equipmentFits(p, l)) choices.add(l);
    if (p.discomfort) return alternative(choices, "recovery", "discomfort");
    if (p.recoveryDay) return alternative(choices, "recovery", "recovery");
    if (p.inactiveDays >= 7) return alternative(choices, "comeback", "comeback");
    List<WorkoutLesson> normal = new ArrayList<>();
    for (WorkoutLesson l : choices) if (!l.getStandalone()) normal.add(l);
    if (normal.isEmpty()) return alternative(choices, "recovery", "equipment");
    String previous = p.lastDifficulties.isEmpty() ? "" : p.lastDifficulties.get(0);
    if ("too_difficult".equals(previous) || "tired".equals(previous)) {
      Collections.sort(
          normal, (a, b) -> Integer.compare(a.getEstimatedMinutes(), b.getEstimatedMinutes()));
      return new Recommendation(normal.get(0), "easier");
    }
    if (p.recentCategories.contains("strength")) {
      for (WorkoutLesson l : normal)
        if ("mobility".equals(l.getCategory())) return new Recommendation(l, "balance");
      return alternative(choices, "recovery", "balance");
    }
    if (p.lastDifficulties.size() >= 2
        && "too_easy".equals(previous)
        && "too_easy".equals(p.lastDifficulties.get(1))) {
      Collections.sort(normal, (a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
      return new Recommendation(normal.get(normal.size() - 1), "next");
    }
    if (p.lastDifficulties.isEmpty() && !p.durationPreferenceKnown && p.minutes == 10) {
      Collections.sort(normal, (a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
      return new Recommendation(normal.get(0), "beginner");
    }
    Collections.sort(normal, (a, b) -> Integer.compare(score(p, a), score(p, b)));
    return new Recommendation(normal.get(0), p.minutes <= 5 ? "micro" : "preference");
  }

  boolean equipmentFits(Preferences p, WorkoutLesson l) {
    if (p.equipment.isEmpty()) return true;
    for (String e : l.getEquipment())
      if (!"none".equals(e) && !p.equipment.contains(e)) return false;
    return true;
  }

  private int score(Preferences p, WorkoutLesson l) {
    int score = Math.abs(p.minutes - l.getEstimatedMinutes()) * 10 + l.getSequence();
    if (!l.getDifficulty().equalsIgnoreCase(p.fitnessLevel)) score += 10;
    if (p.categories.contains(l.getCategory())
        || p.goal.toLowerCase(Locale.ROOT).contains(l.getCategory())) score -= 5;
    return score;
  }

  private Recommendation alternative(List<WorkoutLesson> list, String id, String reason) {
    for (WorkoutLesson l : list) if (id.equals(l.getId())) return new Recommendation(l, reason);
    return new Recommendation(null, reason);
  }
}
