package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.Values;
import java.util.*;
import org.junit.Test;

public class RecommendationContextTest {
  @Test
  public void scheduledRecoveryDay() {
    Calendar cal = new GregorianCalendar(2026, 9, 28, 12, 0);
    RecommendationService.Context c =
        new RecommendationContextFactory()
            .create(
                Values.of("preferredWorkoutDays", Arrays.asList(1, 5)),
                Collections.emptyList(),
                cal.getTime(),
                cal.getTimeZone(),
                null,
                null);
    assertTrue(c.recoveryDay);
  }

  @Test
  public void discomfortStillAppliesOnDaySeven() {
    Date now = new Date();
    RecommendationService.Context c =
        new RecommendationContextFactory()
            .create(
                Collections.emptyMap(),
                Collections.emptyList(),
                now,
                TimeZone.getTimeZone("UTC"),
                null,
                new Date(now.getTime() - 7L * 86400000));
    assertTrue(c.discomfort);
  }

  @Test
  public void missingProfileUsesDefaults() {
    RecommendationService.Context c =
        new RecommendationContextFactory()
            .create(
                Collections.emptyMap(),
                Collections.emptyList(),
                new Date(),
                TimeZone.getDefault(),
                null,
                null);
    assertEquals(10, c.preferredMinutes);
    assertEquals("beginner", c.fitnessLevel);
    assertFalse(c.recoveryDay);
  }

  @Test
  public void numericProfileAcceptsDouble() {
    RecommendationService.Context c =
        new RecommendationContextFactory()
            .create(
                Values.of("preferredWorkoutMinutes", 5.0),
                Collections.emptyList(),
                new Date(),
                TimeZone.getDefault(),
                null,
                null);
    assertEquals(5, c.preferredMinutes);
  }
}
