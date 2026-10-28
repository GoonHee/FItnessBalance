package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.model.WorkoutLesson;
import java.util.*;
import org.junit.Test;

public class RecommendationServiceTest {
  private WorkoutLesson lesson(String id, int min, boolean standalone) {
    WorkoutLesson l = new WorkoutLesson();
    l.setId(id);
    l.setName(id);
    l.setEstimatedMinutes(min);
    l.setStandalone(standalone);
    l.setDifficulty("beginner");
    l.setSequence(min);
    return l;
  }

  private List<WorkoutLesson> all() {
    return Arrays.asList(
        lesson("lesson_01", 5, false),
        lesson("lesson_02", 10, false),
        lesson("recovery", 5, true),
        lesson("comeback", 5, true));
  }

  private String selected(RecommendationService.Preferences p) {
    return new RecommendationService().recommend(p, all()).lesson.getId();
  }

  @Test
  public void insufficientDataUsesBeginner() {
    assertEquals("lesson_01", selected(new RecommendationService.Preferences()));
  }

  @Test
  public void shortPreference() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.minutes = 5;
    assertEquals("lesson_01", selected(p));
  }

  @Test
  public void comeback() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.inactiveDays = 7;
    assertEquals("comeback", selected(p));
  }

  @Test
  public void discomfortPreventsIncreaseEvenAfterInactivity() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.inactiveDays = 10;
    p.discomfort = true;
    p.lastDifficulties = Arrays.asList("too_easy", "too_easy");
    assertEquals("recovery", selected(p));
  }

  @Test
  public void scheduledRecovery() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.recoveryDay = true;
    assertEquals("recovery", selected(p));
  }

  @Test
  public void difficultChoosesShorter() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.lastDifficulties = Arrays.asList("too_difficult");
    assertEquals("lesson_01", selected(p));
  }

  @Test
  public void easyChoosesLaterAvailable() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.lastDifficulties = Arrays.asList("too_easy", "too_easy");
    assertEquals("lesson_02", selected(p));
  }

  @Test
  public void recentStrengthChoosesRecovery() {
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.recentCategories = Arrays.asList("strength");
    assertEquals("recovery", selected(p));
  }

  @Test
  public void missingContentReturnsNoLesson() {
    assertNull(
        new RecommendationService()
            .recommend(new RecommendationService.Preferences(), Collections.emptyList())
            .lesson);
  }

  @Test
  public void equipmentIsRespectedForAlternatives() {
    WorkoutLesson l = lesson("recovery", 5, true);
    l.setEquipment(Arrays.asList("chair"));
    RecommendationService.Preferences p = new RecommendationService.Preferences();
    p.discomfort = true;
    p.equipment = Arrays.asList("none");
    assertNull(new RecommendationService().recommend(p, Arrays.asList(l)).lesson);
  }
}
