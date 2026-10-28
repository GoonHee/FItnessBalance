package com.example.fitnessbalance.model;

import java.util.*;

/** Unified read model; existing manual and immutable guided records retain their storage paths. */
public final class ActivityDay {
  public final String day;
  public final List<Map<String, Object>> manual, guided;
  public final Map<String, Object> totals;
  public final int caloriesConsumed;

  public ActivityDay(
      String day,
      List<Map<String, Object>> manual,
      List<Map<String, Object>> guided,
      Map<String, Object> totals,
      int consumed) {
    this.day = day;
    this.manual = manual;
    this.guided = guided;
    this.totals = totals;
    this.caloriesConsumed = consumed;
  }
}
