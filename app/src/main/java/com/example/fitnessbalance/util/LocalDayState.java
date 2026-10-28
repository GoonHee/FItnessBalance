package com.example.fitnessbalance.util;

/** Calendar-day transitions, including restored screens and backwards clock changes. */
public final class LocalDayState {
  private String day;

  public LocalDayState(String day) {
    this.day = day;
  }

  public String day() {
    return day;
  }

  public boolean update(String today) {
    if (today.equals(day)) return false;
    day = today;
    return true;
  }

  public static String selection(String selected, String observed, String today) {
    return selected != null && today.equals(observed) ? selected : today;
  }
}
