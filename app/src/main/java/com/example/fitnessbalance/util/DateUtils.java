package com.example.fitnessbalance.util;

import java.text.SimpleDateFormat;
import java.util.*;

public final class DateUtils {
  private DateUtils() {}

  public static String day(Date time, TimeZone zone) {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    f.setTimeZone(zone);
    return f.format(time);
  }

  /** Calendar midnight, rather than a fixed 24 hours (DST days may be shorter/longer). */
  public static long untilNextDay(Date now, TimeZone zone) {
    Calendar c = Calendar.getInstance(zone);
    c.setTime(now);
    c.add(Calendar.DATE, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return Math.max(1, c.getTimeInMillis() - now.getTime());
  }

  public static String week(Date time, TimeZone zone) {
    Calendar c = Calendar.getInstance(zone);
    c.setTime(time);
    c.add(Calendar.DATE, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
    return day(c.getTime(), zone);
  }

  public static int daysBetween(Date from, Date to, TimeZone zone) {
    if (from == null || to == null || from.after(to)) return 0;
    Calendar c = Calendar.getInstance(zone);
    c.setTime(from);
    int days = 0;
    String end = day(to, zone);
    while (day(c.getTime(), zone).compareTo(end) < 0 && days < 3650) {
      c.add(Calendar.DATE, 1);
      days++;
    }
    return days;
  }

  public static int isoDay(Date time, TimeZone zone) {
    Calendar c = Calendar.getInstance(zone);
    c.setTime(time);
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
  }
}
