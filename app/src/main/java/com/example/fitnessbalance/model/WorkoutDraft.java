package com.example.fitnessbalance.model;

import com.example.fitnessbalance.service.SessionState;

public final class WorkoutDraft {
  public String userId = "", sessionId = "", pathId = "", difficulty = "", feeling = "";
  public LessonPlan plan = new LessonPlan();
  public SessionState state = new SessionState();
  public double weight = 70, calories;
}
