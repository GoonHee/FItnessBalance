package com.example.fitnessbalance.service;

public final class GamificationService {
  public double completion(int completed, int planned) {
    return planned <= 0 ? 0 : 100.0 * Math.max(0, Math.min(completed, planned)) / planned;
  }

  public int workoutXp(int base, double percentage, int previousAttempts) {
    double modifier = percentage >= 80 ? 1 : percentage >= 50 ? .6 : .25;
    double repeat = previousAttempts <= 0 ? 1 : previousAttempts == 1 ? .5 : .25;
    return (int) Math.round(Math.max(0, Math.min(base, 35)) * modifier * repeat);
  }

  public int level(int totalXp) {
    return Math.max(0, totalXp) / 100 + 1;
  }

  public int toNextLevel(int totalXp) {
    return 100 - Math.max(0, totalXp) % 100;
  }

  public int stars(double percentage, String difficulty) {
    return percentage < 50 ? 1 : percentage >= 80 && "suitable".equals(difficulty) ? 3 : 2;
  }

  public boolean unlocked(String prerequisite, double best) {
    return prerequisite == null || prerequisite.isEmpty() || best >= 50;
  }

  public boolean canAward(boolean recordExists) {
    return !recordExists;
  }
}
