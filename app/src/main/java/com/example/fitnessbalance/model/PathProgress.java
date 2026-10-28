package com.example.fitnessbalance.model;

import java.util.*;

public class PathProgress {
  private Map<String, Integer> completionCounts = new HashMap<>();
  private Map<String, Double> bestPercent = new HashMap<>();
  private Map<String, Integer> bestStars = new HashMap<>();

  public PathProgress() {}

  public Map<String, Integer> getCompletionCounts() {
    return completionCounts;
  }

  public void setCompletionCounts(Map<String, Integer> value) {
    completionCounts = value == null ? new HashMap<>() : value;
  }

  public Map<String, Double> getBestPercent() {
    return bestPercent;
  }

  public void setBestPercent(Map<String, Double> value) {
    bestPercent = value == null ? new HashMap<>() : value;
  }

  public Map<String, Integer> getBestStars() {
    return bestStars;
  }

  public void setBestStars(Map<String, Integer> value) {
    bestStars = value == null ? new HashMap<>() : value;
  }
}
