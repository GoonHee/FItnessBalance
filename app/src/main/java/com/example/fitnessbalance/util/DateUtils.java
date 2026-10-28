package com.example.fitnessbalance.util;

import java.text.SimpleDateFormat;
import java.util.*;

public final class DateUtils {
  private DateUtils() {}

  public static String day(Date date, TimeZone zone) {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    f.setTimeZone(zone);
    return f.format(date);
  }

  public static String week(Date date, TimeZone zone) {
    Calendar c = Calendar.getInstance(zone);
    c.setTime(date);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    int offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    c.add(Calendar.DATE, -offset);
    return day(c.getTime(), zone);
  }

  public static int daysSince(Date past, Date now, TimeZone zone) {
    if (past == null) return 0;
    Calendar c = Calendar.getInstance(zone);
    c.setTime(past);
    int days = 0;
    while (day(c.getTime(), zone).compareTo(day(now, zone)) < 0 && days < 3650) {
      c.add(Calendar.DATE, 1);
      days++;
    }
    return days;
  }
}
