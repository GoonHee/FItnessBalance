package com.example.fitnessbalance.service;

/** Gross MET estimates, using the same calculation as guided sessions. Never used for XP. */
public final class ExerciseCalorieService {
  public double weight(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) || value <= 0 || value > 300
        ? 70
        : value;
  }

  public long estimate(double met, double weight, double minutes) {
    if (!finite(met) || met < 1 || met > 20 || !finite(minutes) || minutes <= 0 || minutes > 1440)
      throw new IllegalArgumentException("Invalid activity target");
    return Math.round(met * 3.5 * weight(weight) / 200.0 * minutes);
  }

  private boolean finite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }
}
