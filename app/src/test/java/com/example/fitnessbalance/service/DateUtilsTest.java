package com.example.fitnessbalance.service;

import static org.junit.Assert.*;

import com.example.fitnessbalance.util.DateUtils;
import java.util.*;
import org.junit.Test;

public class DateUtilsTest {
  @Test
  public void localDateDiffersFromUtc() {
    Date d = new Date(0);
    assertEquals("1969-12-31", DateUtils.day(d, TimeZone.getTimeZone("America/Los_Angeles")));
    assertEquals("1970-01-01", DateUtils.day(d, TimeZone.getTimeZone("Asia/Kuala_Lumpur")));
  }

  @Test
  public void weekUsesMondayAcrossYear() {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.set(2026, 0, 1, 12, 0);
    assertEquals("2025-12-29", DateUtils.week(c.getTime(), c.getTimeZone()));
  }

  @Test
  public void daysAreCalendarDays() {
    TimeZone z = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    Calendar a = Calendar.getInstance(z);
    a.set(2026, 9, 28, 23, 59);
    Calendar b = (Calendar) a.clone();
    b.add(Calendar.MINUTE, 2);
    assertEquals(1, DateUtils.daysBetween(a.getTime(), b.getTime(), z));
  }
}
