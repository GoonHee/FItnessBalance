package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.model.WorkoutExercise;
import java.util.*;
import org.junit.Test;

public class SessionStateTest {
  private List<WorkoutExercise> plan() {
    List<WorkoutExercise> p = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      WorkoutExercise e = new WorkoutExercise();
      e.setTargetType(i % 2 == 0 ? "duration" : "repetitions");
      e.setTargetValue(i % 2 == 0 ? 30 : 8);
      e.setRestAfterSeconds(i == 3 ? 0 : 20);
      p.add(e);
    }
    return p;
  }

  private SessionState start() {
    SessionState s = new SessionState();
    s.initialize(plan());
    s.paused = false;
    return s;
  }

  @Test
  public void timedCountdown() {
    SessionState s = start();
    s.tick(1000, plan());
    assertEquals(29000, s.remainingMs);
    assertEquals(1000, s.activeMs);
  }

  @Test
  public void pauseFreezesTimers() {
    SessionState s = start();
    s.paused = true;
    s.tick(5000, plan());
    assertEquals(30000, s.remainingMs);
    assertEquals(0, s.elapsedMs);
  }

  @Test
  public void expiredTimerWaitsForUser() {
    SessionState s = start();
    s.tick(31000, plan());
    assertEquals(0, s.remainingMs);
    assertEquals(0, s.index);
    assertEquals(0, s.completed);
  }

  @Test
  public void restAdvancesToRepetitions() {
    SessionState s = start();
    s.complete(false, plan());
    assertEquals(SessionState.REST, s.phase);
    s.tick(20000, plan());
    assertEquals(1, s.index);
    assertEquals(SessionState.EXERCISE, s.phase);
    assertEquals(0, s.remainingMs);
    assertEquals(0, s.activeMs);
  }

  @Test
  public void cannotCompleteDuringRest() {
    SessionState s = start();
    s.complete(false, plan());
    assertFalse(s.complete(false, plan()));
    assertEquals(1, s.completed);
  }

  @Test
  public void skipReducesCompletion() {
    SessionState s = start();
    s.complete(true, plan());
    assertEquals(1, s.skipped);
    assertEquals(0, s.completed);
  }

  @Test
  public void fourExercisesReachFeedbackOnce() {
    SessionState s = start();
    for (int i = 0; i < 4; i++) {
      assertTrue(s.complete(false, plan()));
      s.tick(20000, plan());
    }
    assertEquals(SessionState.FEEDBACK, s.phase);
    assertEquals(4, s.completed);
    assertFalse(s.complete(false, plan()));
  }

  @Test
  public void endKeepsPartialCounts() {
    SessionState s = start();
    s.complete(false, plan());
    s.end();
    assertEquals(1, s.completed);
    assertEquals(SessionState.FEEDBACK, s.phase);
    assertTrue(s.paused);
  }

  @Test
  public void activeMinutesExcludeRest() {
    SessionState s = start();
    s.tick(30000, plan());
    s.complete(false, plan());
    s.tick(20000, plan());
    assertEquals(50000, s.elapsedMs);
    assertEquals(30000, s.activeMs);
  }
}
