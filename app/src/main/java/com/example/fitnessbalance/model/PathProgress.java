package com.example.fitnessbalance.model;

import java.util.*;

public class PathProgress {
  public PathProgress() {}

  private Map<String, Object> completionCounts = new HashMap<>();

  public Map<String, Object> getCompletionCounts() {
    return completionCounts;
  }

  public void setCompletionCounts(Map<String, Object> value) {
    completionCounts = value == null ? new HashMap<>() : value;
  }

  private Map<String, Object> bestPercent = new HashMap<>();

  public Map<String, Object> getBestPercent() {
    return bestPercent;
  }

  public void setBestPercent(Map<String, Object> value) {
    bestPercent = value == null ? new HashMap<>() : value;
  }

  private Map<String, Object> bestStars = new HashMap<>();

  public Map<String, Object> getBestStars() {
    return bestStars;
  }

  public void setBestStars(Map<String, Object> value) {
    bestStars = value == null ? new HashMap<>() : value;
  }
}
