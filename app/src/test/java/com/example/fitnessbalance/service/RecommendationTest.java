package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.model.WorkoutLesson;
import java.util.*;
import org.junit.Test;

public class RecommendationTest {
  private WorkoutLesson lesson(String id, int minutes, boolean standalone) {
    WorkoutLesson l = new WorkoutLesson();
    l.setId(id);
    l.setEstimatedMinutes(minutes);
    l.setStandalone(standalone);
    l.setDifficulty("beginner");
    return l;
  }

  private List<WorkoutLesson> all() {
    return Arrays.asList(
        lesson("lesson_01", 5, false),
        lesson("lesson_02", 10, false),
        lesson("recovery", 5, true),
        lesson("comeback", 5, true));
  }

  @Test
  public void discomfortOverridesComebackAndEasy() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.discomfort = true;
    c.inactiveDays = 9;
    c.recentDifficulties = Arrays.asList("too_easy", "too_easy");
    assertEquals("recovery", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void comebackAfterWeek() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.inactiveDays = 7;
    assertEquals("comeback", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void difficultChoosesShorter() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.previousDifficulty = "too_difficult";
    assertEquals("lesson_01", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void microPreference() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.preferredMinutes = 5;
    assertEquals("lesson_01", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void recoveryDay() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.recoveryDay = true;
    assertEquals("recovery", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void twoEasySuggestsAvailableNext() {
    RecommendationService.Context c = new RecommendationService.Context();
    c.recentDifficulties = Arrays.asList("too_easy", "too_easy");
    assertEquals("lesson_02", new RecommendationService().recommend(c, all()).lesson.getId());
  }

  @Test
  public void missingContentReturnsEmpty() {
    assertNull(
        new RecommendationService()
            .recommend(new RecommendationService.Context(), Collections.emptyList())
            .lesson);
  }
}
