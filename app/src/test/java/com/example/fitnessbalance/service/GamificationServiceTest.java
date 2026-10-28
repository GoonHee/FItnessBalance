package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import org.junit.Test;

public class GamificationServiceTest {
  private final GamificationService g = new GamificationService();

  @Test
  public void completionUsesExerciseCounts() {
    assertEquals(75, g.completion(3, 4), 0);
  }

  @Test
  public void emptyPlan() {
    assertEquals(0, g.completion(0, 0), 0);
  }

  @Test
  public void invalidCountsAreClamped() {
    assertEquals(0, g.completion(-1, 4), 0);
    assertEquals(100, g.completion(7, 4), 0);
  }

  @Test
  public void fullXp() {
    assertEquals(20, g.workoutXp(20, 100, 0));
    assertEquals(20, g.workoutXp(20, 80, 0));
  }

  @Test
  public void partialXp() {
    assertEquals(12, g.workoutXp(20, 79.99, 0));
    assertEquals(12, g.workoutXp(20, 50, 0));
  }

  @Test
  public void lowXp() {
    assertEquals(5, g.workoutXp(20, 49.99, 0));
    assertEquals(5, g.workoutXp(20, 0, 0));
  }

  @Test
  public void repeatXp() {
    assertEquals(10, g.workoutXp(20, 100, 1));
    assertEquals(5, g.workoutXp(20, 100, 2));
    assertEquals(5, g.workoutXp(20, 100, 100));
  }

  @Test
  public void feedbackIsPerRecord() {
    assertEquals(2, g.feedbackXp(false));
    assertEquals(0, g.feedbackXp(true));
  }

  @Test
  public void dailyQuestOnce() {
    assertEquals(5, g.dailyBonus(true, false, 5));
    assertEquals(0, g.dailyBonus(true, true, 5));
    assertEquals(0, g.dailyBonus(false, false, 5));
  }

  @Test
  public void weeklyBonusOnce() {
    assertEquals(30, g.weeklyBonus(3, 3, false));
    assertEquals(0, g.weeklyBonus(6, 3, true));
    assertEquals(0, g.weeklyBonus(2, 3, false));
  }

  @Test
  public void levelBoundaries() {
    assertEquals(1, g.level(0));
    assertEquals(1, g.level(99));
    assertEquals(2, g.level(100));
    assertEquals(3, g.level(250));
    assertEquals(50, g.nextLevelXp(250));
  }

  @Test
  public void starsRequireSuitableForThree() {
    assertEquals(1, g.stars(49, "suitable"));
    assertEquals(2, g.stars(50, "suitable"));
    assertEquals(2, g.stars(100, "too_difficult"));
    assertEquals(3, g.stars(80, "suitable"));
  }

  @Test
  public void prerequisiteThreshold() {
    assertTrue(g.unlocked(null, 0));
    assertTrue(g.unlocked("", 0));
    assertFalse(g.unlocked("lesson_01", 49.99));
    assertTrue(g.unlocked("lesson_01", 50));
  }

  @Test
  public void momentumClamps() {
    MomentumService m = new MomentumService();
    assertEquals(66, m.percentage(2, 3));
    assertEquals(100, m.percentage(10, 3));
    assertEquals(0, m.percentage(-1, 3));
    assertEquals(1, m.target(0));
    assertEquals(7, m.target(8));
  }
}
