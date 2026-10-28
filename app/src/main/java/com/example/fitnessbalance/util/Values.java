package com.example.fitnessbalance.util;

import java.util.*;

public final class Values {
  private Values() {}

  public static int integer(Object v, int fallback) {
    return v instanceof Number ? ((Number) v).intValue() : fallback;
  }

  public static double decimal(Object v, double fallback) {
    if (!(v instanceof Number)) return fallback;
    double d = ((Number) v).doubleValue();
    return Double.isNaN(d) || Double.isInfinite(d) ? fallback : d;
  }

  public static String text(Object v, String fallback) {
    return v instanceof String ? (String) v : fallback;
  }

  public static Map<String, Object> map(Object v) {
    Map<String, Object> out = new HashMap<>();
    if (v instanceof Map)
      for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
        if (e.getKey() instanceof String) out.put((String) e.getKey(), e.getValue());
    return out;
  }

  public static List<String> strings(Object v) {
    List<String> out = new ArrayList<>();
    if (v instanceof List)
      for (Object e : (List<?>) v) if (e instanceof String) out.add((String) e);
    return out;
  }

  public static Map<String, Object> of(Object... pairs) {
    Map<String, Object> out = new HashMap<>();
    for (int n = 0; n < pairs.length; n += 2) out.put((String) pairs[n], pairs[n + 1]);
    return out;
  }
}
