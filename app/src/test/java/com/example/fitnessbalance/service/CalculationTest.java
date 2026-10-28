package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.DateUtils;
import java.util.*;
import org.junit.Test;

public class CalculationTest {
  private final GamificationService g = new GamificationService();

  @Test
  public void completionClampsAndHandlesZero() {
    assertEquals(0, g.completion(2, 0), 0);
    assertEquals(50, g.completion(2, 4), 0);
    assertEquals(100, g.completion(9, 4), 0);
    assertEquals(0, g.completion(-1, 4), 0);
  }

  @Test
  public void xpThresholds() {
    assertEquals(20, g.workoutXp(20, 100, 0));
    assertEquals(20, g.workoutXp(20, 80, 0));
    assertEquals(12, g.workoutXp(20, 79.99, 0));
    assertEquals(12, g.workoutXp(20, 50, 0));
    assertEquals(5, g.workoutXp(20, 49.99, 0));
  }

  @Test
  public void repeatsReduceWorkoutOnly() {
    assertEquals(10, g.workoutXp(20, 100, 1));
    assertEquals(5, g.workoutXp(20, 100, 2));
    assertEquals(5, g.workoutXp(20, 100, 30));
  }

  @Test
  public void levels() {
    assertEquals(1, g.level(0));
    assertEquals(2, g.level(100));
    assertEquals(3, g.level(299));
    assertEquals(1, g.toNextLevel(299));
  }

  @Test
  public void stars() {
    assertEquals(1, g.stars(49, "suitable"));
    assertEquals(2, g.stars(50, "suitable"));
    assertEquals(3, g.stars(80, "suitable"));
    assertEquals(2, g.stars(100, "too_easy"));
  }

  @Test
  public void unlockThreshold() {
    assertTrue(g.unlocked(null, 0));
    assertFalse(g.unlocked("lesson_01", 49.99));
    assertTrue(g.unlocked("lesson_01", 50));
  }

  @Test
  public void momentum() {
    MomentumService m = new MomentumService();
    assertEquals(0, m.percentage(0, 3));
    assertEquals(66, m.percentage(2, 3));
    assertEquals(100, m.percentage(6, 3));
    assertEquals(100, m.percentage(1, 0));
    assertEquals("strong", m.band(80));
  }

  @Test
  public void duplicateCannotAward() {
    assertFalse(g.canAward(true));
    assertTrue(g.canAward(false));
  }

  @Test
  public void weekUsesLocalMondayAcrossYear() {
    TimeZone z = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    assertEquals("2025-12-29", DateUtils.week(new GregorianCalendar(2026, 0, 1).getTime(), z));
  }
}
