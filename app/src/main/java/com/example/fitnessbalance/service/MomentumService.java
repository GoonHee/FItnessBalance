package com.example.fitnessbalance.service;

public final class MomentumService {
  public int target(int target) {
    return Math.max(1, Math.min(7, target));
  }

  public int percentage(int done, int target) {
    return (int) Math.min(100, 100.0 * Math.max(0, done) / target(target));
  }

  public int band(int percent) {
    return percent >= 80 ? 3 : percent >= 50 ? 2 : percent > 0 ? 1 : 0;
  }
}
