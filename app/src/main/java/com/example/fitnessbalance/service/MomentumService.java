package com.example.fitnessbalance.service;

public final class MomentumService {
  public int target(int value) {
    return Math.max(1, Math.min(7, value));
  }

  public int percentage(int completed, int target) {
    return Math.min(100, (int) Math.floor(100.0 * Math.max(0, completed) / target(target)));
  }

  public String band(int percent) {
    return percent >= 80
        ? "strong"
        : percent >= 50 ? "building" : percent > 0 ? "restart" : "begin";
  }
}
