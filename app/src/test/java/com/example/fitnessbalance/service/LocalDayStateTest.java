package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.DateUtils;
import com.example.fitnessbalance.util.LocalDayState;
import java.util.*;
import org.junit.Test;

public class LocalDayStateTest {
  @Test
  public void midnightAndResumeAfterSeveralDaysRefreshOnce() {
    LocalDayState state = new LocalDayState("2026-10-28");
    assertFalse(state.update("2026-10-28"));
    assertTrue(state.update("2026-10-29"));
    assertFalse(state.update("2026-10-29"));
    assertTrue(state.update("2026-11-02"));
    assertEquals("2026-11-02", state.day());
  }

  @Test
  public void backwardsClockOrTimezoneChangeRefreshes() {
    LocalDayState state = new LocalDayState("2026-10-29");
    assertTrue(state.update("2026-10-28"));
  }

  @Test
  public void historySurvivesSameDayRotationButNotNextDayRestore() {
    assertEquals("2026-10-20", LocalDayState.selection("2026-10-20", "2026-10-28", "2026-10-28"));
    assertEquals("2026-10-29", LocalDayState.selection("2026-10-20", "2026-10-28", "2026-10-29"));
    assertEquals("2026-10-29", LocalDayState.selection(null, null, "2026-10-29"));
  }

  private Date instant(
      String zone, int year, int month, int day, int hour, int minute, int second) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
    c.clear();
    c.set(year, month - 1, day, hour, minute, second);
    return c.getTime();
  }

  @Test
  public void schedulesLocalMidnightAcrossMonthAndYear() {
    TimeZone z = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    Date now = instant(z.getID(), 2026, 12, 31, 23, 59, 59);
    assertEquals(1000, DateUtils.untilNextDay(now, z));
    assertEquals(
        "2027-01-01", DateUtils.day(new Date(now.getTime() + DateUtils.untilNextDay(now, z)), z));
  }

  @Test
  public void daylightSavingDaysAreNotAlwaysTwentyFourHours() {
    TimeZone z = TimeZone.getTimeZone("America/New_York");
    assertEquals(23 * 3600000L, DateUtils.untilNextDay(instant(z.getID(), 2026, 3, 8, 0, 0, 0), z));
    assertEquals(
        25 * 3600000L, DateUtils.untilNextDay(instant(z.getID(), 2026, 11, 1, 0, 0, 0), z));
  }
}
