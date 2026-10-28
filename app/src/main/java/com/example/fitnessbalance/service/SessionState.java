package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutExercise;
import java.util.*;

/** Pure session state machine. The UI supplies monotonic time deltas and persists snapshots. */
public final class SessionState {
  public static final String EXERCISE = "exercise", REST = "rest", FEEDBACK = "feedback";
  public int index, completed, skipped;
  public long remainingMs, elapsedMs, activeMs;
  public boolean paused = true;
  public String phase = EXERCISE;

  public void initialize(List<WorkoutExercise> plan) {
    if (plan.isEmpty()) throw new IllegalArgumentException("Empty plan");
    remainingMs = targetMs(plan.get(0));
  }

  public void tick(long delta, List<WorkoutExercise> plan) {
    if (paused || FEEDBACK.equals(phase) || delta <= 0) return;
    elapsedMs += delta;
    if (EXERCISE.equals(phase)) activeMs += delta;
    remainingMs = Math.max(0, remainingMs - delta);
    if (REST.equals(phase) && remainingMs == 0) next(plan);
  }

  public boolean complete(boolean skip, List<WorkoutExercise> plan) {
    if (paused || !EXERCISE.equals(phase)) return false;
    if (skip) skipped++;
    else completed++;
    if (index == plan.size() - 1) {
      end();
      return true;
    }
    int rest = plan.get(index).getRestAfterSeconds();
    if (rest > 0) {
      phase = REST;
      remainingMs = rest * 1000L;
    } else next(plan);
    return true;
  }

  public void end() {
    phase = FEEDBACK;
    paused = true;
  }

  private void next(List<WorkoutExercise> plan) {
    index++;
    phase = EXERCISE;
    remainingMs = targetMs(plan.get(index));
  }

  private long targetMs(WorkoutExercise e) {
    return "duration".equals(e.getTargetType()) ? e.getTargetValue() * 1000L : 0;
  }
}
