package com.example.fitnessbalance.health;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HealthSummaryPolicyTest {
    private final long now = 1800000000000L;
    @Test public void freshnessHasExplicitBoundary() {
        assertFalse(HealthSummaryPolicy.stale(now, now));
        assertFalse(HealthSummaryPolicy.stale(now - HealthSummaryPolicy.MAX_AGE_MS + 1, now));
        assertTrue(HealthSummaryPolicy.stale(now - HealthSummaryPolicy.MAX_AGE_MS, now));
    }
    @Test public void unknownOrFutureTimeIsNotFresh() {
        assertTrue(HealthSummaryPolicy.stale(null, now));
        assertTrue(HealthSummaryPolicy.stale("yesterday", now));
        assertTrue(HealthSummaryPolicy.stale(0, now));
        assertTrue(HealthSummaryPolicy.stale(now + 1000, now));
    }
    @Test public void revokedMetricIsRemovedWithoutOverwritingOtherData() {
        Map<String,Object> steps = new HashMap<>(); steps.put("value", 700L); steps.put("state", "available");
        Map<String,Object> heart = new HashMap<>(); heart.put("value", 75L); heart.put("observedAt", now);
        Map<String,Object> source = new HashMap<>(); source.put("steps", steps); source.put("heart", heart); source.put("syncedAt", now);
        Map<String,Object> masked = HealthSummaryPolicy.mask(source, Collections.singleton("steps"));
        assertEquals(steps, masked.get("steps")); assertEquals(now, masked.get("syncedAt"));
        Map<?,?> hidden = (Map<?,?>)masked.get("heart");
        assertEquals("permission_required", hidden.get("state"));
        assertFalse(hidden.containsKey("value")); assertFalse(hidden.containsKey("observedAt"));
        assertEquals(75L, ((Map<?,?>)source.get("heart")).get("value"));
    }
    @Test public void missingDataIsNeverInventedAsZero() {
        Set<String> all = new HashSet<>(Arrays.asList("steps", "sleep", "heart"));
        Map<String,Object> masked = HealthSummaryPolicy.mask(Collections.emptyMap(), all);
        assertFalse(masked.containsKey("sleep")); assertFalse(masked.containsKey("heart"));
        assertFalse(masked.containsKey("syncedAt")); assertFalse(masked.containsKey("caloriesBurned"));
    }
    @Test public void fullRevocationHidesAllValues() {
        Map<String,Object> source = new HashMap<>();
        for (String key : Arrays.asList("steps", "sleep", "heart")) source.put(key, Collections.singletonMap("value", 123L));
        Map<String,Object> masked = HealthSummaryPolicy.mask(source, Collections.emptySet());
        for (String key : Arrays.asList("steps", "sleep", "heart")) {
            assertEquals(Collections.singletonMap("state", "permission_required"), masked.get(key));
        }
    }
}
