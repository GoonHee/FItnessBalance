package com.example.fitnessbalance.service;

/** Application progress only. Calories and missed days never influence XP. */
public final class GamificationService {
  public static final int MAX_LESSON_XP = 35;
  public static final int FEEDBACK_XP = 2;
  public static final int DAILY_QUEST_XP = 5;
  public static final int WEEKLY_TARGET_XP = 30;
  public static final int XP_PER_LEVEL = 100;
  public static final int MIN_GUIDED_ACTIVE_SECONDS = 60;
  public static final int ACTIVE_MINUTES_QUEST_SECONDS = 300;

  /** A short or overlapping session can still be saved, including its feedback. */
  public boolean eligibleGuidedWorkout(int activeSeconds, boolean countedWearableOverlap) {
    return activeSeconds >= MIN_GUIDED_ACTIVE_SECONDS && !countedWearableOverlap;
  }

  public double completion(int done, int planned) {
    return planned <= 0 ? 0 : 100.0 * Math.max(0, Math.min(done, planned)) / planned;
  }

  public int workoutXp(int base, double completion, int previousAttempts) {
    double fraction = completion >= 80 ? 1 : completion >= 50 ? .6 : .25;
    double repeat = previousAttempts <= 0 ? 1 : previousAttempts == 1 ? .5 : .25;
    return (int) Math.round(Math.max(0, Math.min(MAX_LESSON_XP, base)) * fraction * repeat);
  }

  public int level(int xp) {
    return Math.max(0, xp) / XP_PER_LEVEL + 1;
  }

  public int nextLevelXp(int xp) {
    return XP_PER_LEVEL - Math.max(0, xp) % XP_PER_LEVEL;
  }

  public int stars(double completion, String difficulty) {
    return completion < 50 ? 1 : completion >= 80 && "suitable".equals(difficulty) ? 3 : 2;
  }

  public boolean unlocked(String prerequisite, double best) {
    return prerequisite == null || prerequisite.isEmpty() || best >= 50;
  }

  public int feedbackXp(boolean recordExists) {
    return recordExists ? 0 : FEEDBACK_XP;
  }

  public int dailyBonus(boolean eligible, boolean alreadyCompleted, int xp) {
    return eligible && !alreadyCompleted ? xp : 0;
  }

  public int weeklyBonus(int completed, int target, boolean alreadyAwarded) {
    return completed >= target && !alreadyAwarded ? WEEKLY_TARGET_XP : 0;
  }
}
