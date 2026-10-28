package com.example.fitnessbalance.service;

/** Application progress only. Calories and missed days never influence XP. */
public final class GamificationService {
  public double completion(int done, int planned) {
    return planned <= 0 ? 0 : 100.0 * Math.max(0, Math.min(done, planned)) / planned;
  }

  public int workoutXp(int base, double completion, int previousAttempts) {
    double fraction = completion >= 80 ? 1 : completion >= 50 ? .6 : .25;
    double repeat = previousAttempts <= 0 ? 1 : previousAttempts == 1 ? .5 : .25;
    return (int) Math.round(Math.max(0, Math.min(35, base)) * fraction * repeat);
  }

  public int level(int xp) {
    return Math.max(0, xp) / 100 + 1;
  }

  public int nextLevelXp(int xp) {
    return 100 - Math.max(0, xp) % 100;
  }

  public int stars(double completion, String difficulty) {
    return completion < 50 ? 1 : completion >= 80 && "suitable".equals(difficulty) ? 3 : 2;
  }

  public boolean unlocked(String prerequisite, double best) {
    return prerequisite == null || prerequisite.isEmpty() || best >= 50;
  }

  public int feedbackXp(boolean recordExists) {
    return recordExists ? 0 : 2;
  }

  public int dailyBonus(boolean eligible, boolean alreadyCompleted, int xp) {
    return eligible && !alreadyCompleted ? xp : 0;
  }

  public int weeklyBonus(int completed, int target, boolean alreadyAwarded) {
    return completed >= target && !alreadyAwarded ? 30 : 0;
  }
}
