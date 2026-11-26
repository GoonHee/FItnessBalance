package com.example.fitnessbalance.repository;

import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DailyChestRepositoryTest {
  @Test public void anyTwoDistinctGoalsEarnAChest() {
    for (int goals = 0; goals < 8; goals++) {
      assertEquals(Integer.bitCount(goals) >= 2, DailyChestRepository.qualifies(
          (goals & 1) != 0, (goals & 2) != 0, (goals & 4) != 0));
    }
  }

  @Test public void restUsesTheSelectedWeekdayAndRequiresAPlan() {
    LocalDate monday = LocalDate.of(2026, 11, 30);
    assertFalse(DailyChestRepository.plannedRest(Collections.emptyList(), monday));
    assertFalse(DailyChestRepository.plannedRest(Arrays.asList(1L, 5L), monday));
    assertTrue(DailyChestRepository.plannedRest(Arrays.asList(1L, 5L), monday.plusDays(1)));
  }

  @Test public void previouslyOpenedReceiptDoesNotTriggerCelebration() {
    DailyChestRepository.Chest saved = DailyChestRepository.restoredOpen("purple");
    assertTrue(saved.opened);
    assertFalse(saved.newlyOpened);
    assertEquals("purple", saved.accent);
  }

  @Test public void rewardChangesAtTheLocalDateBoundary() {
    LocalDate today = LocalDate.of(2026, 12, 31);
    assertNotEquals(DailyChestRepository.accent(today), DailyChestRepository.accent(today.plusDays(1)));
    assertEquals(DailyChestRepository.accent(today), DailyChestRepository.accent(today.plusDays(3)));
  }
}
