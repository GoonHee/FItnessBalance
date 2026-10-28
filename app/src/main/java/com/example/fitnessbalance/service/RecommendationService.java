package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutLesson;
import java.util.*;

/** Deterministic rules. Safety and recovery take precedence over inactivity. */
public final class RecommendationService {
  public static class Context {
    public int inactiveDays, preferredMinutes = 10;
    public boolean discomfort, recoveryDay;
    public String previousDifficulty = "", fitnessLevel = "beginner", goal = "";
    public List<String> recentDifficulties = new ArrayList<>(),
        recentCategories = new ArrayList<>(),
        preferredCategories = new ArrayList<>(),
        equipment = new ArrayList<>();
  }

  public static class Recommendation {
    public final WorkoutLesson lesson;
    public final String reason;

    public Recommendation(WorkoutLesson lesson, String reason) {
      this.lesson = lesson;
      this.reason = reason;
    }
  }

  public Recommendation recommend(Context c, List<WorkoutLesson> available) {
    if (c.discomfort) return choose(available, "recovery", "discomfort");
    if (c.recoveryDay) return choose(available, "recovery", "recovery");
    if (c.inactiveDays >= 7) return choose(available, "comeback", "comeback");
    List<WorkoutLesson> normal = new ArrayList<>();
    for (WorkoutLesson l : available) if (!l.getStandalone() && hasEquipment(c, l)) normal.add(l);
    if (normal.isEmpty()) return choose(available, "recovery", "equipment");
    if ("too_difficult".equals(c.previousDifficulty)) {
      Collections.sort(
          normal, (a, b) -> Integer.compare(a.getEstimatedMinutes(), b.getEstimatedMinutes()));
      return new Recommendation(normal.get(0), "easier");
    }
    if (c.recentCategories.contains("strength")) {
      for (WorkoutLesson l : normal)
        if ("mobility".equals(l.getCategory())) return new Recommendation(l, "balance");
      return choose(available, "recovery", "balance");
    }
    if (c.recentDifficulties.size() >= 2
        && "too_easy".equals(c.recentDifficulties.get(0))
        && "too_easy".equals(c.recentDifficulties.get(1)))
      return new Recommendation(normal.get(normal.size() - 1), "next");
    Collections.sort(normal, (a, b) -> Integer.compare(score(c, a), score(c, b)));
    return new Recommendation(normal.get(0), c.preferredMinutes <= 5 ? "micro" : "preference");
  }

  private int score(Context c, WorkoutLesson l) {
    int s = Math.abs(l.getEstimatedMinutes() - c.preferredMinutes) * 10 + l.getSequence();
    if (c.preferredCategories.contains(l.getCategory())
        || c.goal.toLowerCase(Locale.ROOT).contains(l.getCategory())) s -= 5;
    if (!l.getDifficulty().equalsIgnoreCase(c.fitnessLevel)) s += 20;
    return s;
  }

  private boolean hasEquipment(Context c, WorkoutLesson l) {
    if (c.equipment.isEmpty()) return true;
    for (String e : l.getEquipment())
      if (!"none".equals(e) && !c.equipment.contains(e)) return false;
    return true;
  }

  private Recommendation choose(List<WorkoutLesson> all, String id, String reason) {
    for (WorkoutLesson l : all) if (l.getId().equals(id)) return new Recommendation(l, reason);
    return new Recommendation(null, reason);
  }
}
