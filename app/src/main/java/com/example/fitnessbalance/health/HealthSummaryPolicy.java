package com.example.fitnessbalance.health;

import java.util.*;

/** Pure freshness and permission policy; unavailable values never become zero. */
public final class HealthSummaryPolicy {
    public static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private HealthSummaryPolicy() {}
    public static boolean stale(Object time, long now) {
        if (!(time instanceof Number)) return true;
        long value = ((Number) time).longValue();
        return value <= 0 || value > now || now - value >= MAX_AGE_MS;
    }
    public static Map<String, Object> mask(Map<String, Object> snapshot, Set<String> allowed) {
        Map<String, Object> result = new HashMap<>(snapshot);
        for (String key : Arrays.asList("steps", "sleep", "heart")) {
            if (!allowed.contains(key)) {
                Map<String, Object> hidden = new HashMap<>(); hidden.put("state", "permission_required");
                result.put(key, hidden);
            }
        }
        return result;
    }
}
