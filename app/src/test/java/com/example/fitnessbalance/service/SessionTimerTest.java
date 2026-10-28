package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import org.junit.Test;

public class SessionTimerTest {
  @Test
  public void countdown() {
    assertEquals(29000, new SessionTimer().remaining(30000, 1000, false));
  }

  @Test
  public void pausePreservesRemaining() {
    assertEquals(30000, new SessionTimer().remaining(30000, 10000, true));
  }

  @Test
  public void countdownClamps() {
    assertEquals(0, new SessionTimer().remaining(500, 1000, false));
  }

  @Test
  public void restTransitionsOnlyWhenRunningAndFinished() {
    SessionTimer t = new SessionTimer();
    assertFalse(t.restFinished(true, 500, false));
    assertFalse(t.restFinished(true, 0, true));
    assertTrue(t.restFinished(true, 0, false));
    assertFalse(t.restFinished(false, 0, false));
  }
}
