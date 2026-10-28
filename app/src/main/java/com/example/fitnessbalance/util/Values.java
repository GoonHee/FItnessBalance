package com.example.fitnessbalance.util;

import java.util.*;

public final class Values {
  private Values() {}

  public static int integer(Object value, int fallback) {
    return value instanceof Number ? ((Number) value).intValue() : fallback;
  }

  public static double decimal(Object value, double fallback) {
    return value instanceof Number ? ((Number) value).doubleValue() : fallback;
  }

  public static String text(Object value, String fallback) {
    return value instanceof String ? (String) value : fallback;
  }

  public static Map<String, Object> map(Object value) {
    Map<String, Object> out = new HashMap<>();
    if (value instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
        if (entry.getKey() instanceof String) out.put((String) entry.getKey(), entry.getValue());
    }
    return out;
  }

  public static List<String> strings(Object value) {
    List<String> out = new ArrayList<>();
    if (value instanceof List)
      for (Object v : (List<?>) value) if (v instanceof String) out.add((String) v);
    return out;
  }

  public static Map<String, Object> of(Object... pairs) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }
}
