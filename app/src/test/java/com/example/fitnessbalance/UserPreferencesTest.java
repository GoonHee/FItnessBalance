package com.example.fitnessbalance;

import com.example.fitnessbalance.service.RecommendationPreferences;
import com.example.fitnessbalance.service.RecommendationService;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class UserPreferencesTest {
    private Map<String,Object> settings(int minutes, int target, List<Integer> days,
            List<String> equipment, Map<String,Double> goals) {
        return UserPreferences.validate(minutes, target, days, equipment,
                Arrays.asList("halal", "avoid_pork"), goals);
    }
    @Test public void preferencesReachExistingRecommendationInputs() {
        Map<String,Object> profile = settings(20, 4, Arrays.asList(1,3,5),
                Arrays.asList("chair"), Collections.singletonMap("dailyCalorieGoal", 2100.0));
        TimeZone zone = TimeZone.getTimeZone("UTC");
        Calendar day = Calendar.getInstance(zone); day.clear(); day.set(2026, Calendar.OCTOBER, 27, 12, 0);
        RecommendationService.Preferences p = new RecommendationPreferences().from(profile,
                Collections.emptyList(), null, null, day.getTime(), zone);
        assertEquals(20, p.minutes); assertTrue(p.durationPreferenceKnown);
        assertEquals(Arrays.asList("chair"), p.equipment);
        assertTrue(p.recoveryDay); // Tuesday is outside Monday/Wednesday/Friday.
        day.set(Calendar.DAY_OF_MONTH, 26);
        p = new RecommendationPreferences().from(profile, Collections.emptyList(),
                null, null, day.getTime(), zone);
        assertFalse(p.recoveryDay);
        assertEquals(4, profile.get("weeklyWorkoutTarget"));
        assertEquals(2100.0, (Double) profile.get("dailyCalorieGoal"), 0);
    }
    @Test public void emptyScheduleDoesNotInventRecoveryDaysOrNutritionGoals() {
        Map<String,Object> profile = settings(10, 3, Collections.emptyList(),
                Arrays.asList("none"), Collections.emptyMap());
        RecommendationService.Preferences p = new RecommendationPreferences().from(profile,
                Collections.emptyList(), null, null, new Date(), TimeZone.getTimeZone("UTC"));
        assertFalse(p.recoveryDay);
        for (String key : UserPreferences.GOALS) assertFalse(profile.containsKey(key));
        assertFalse(profile.containsKey("totalXp")); assertFalse(profile.containsKey("level"));
    }
    @Test public void acceptsFractionalMacroTargetsAndDeduplicatesDays() {
        Map<String,Object> p = settings(5, 1, Arrays.asList(1,1,7), Arrays.asList("none"),
                Collections.singletonMap("dailyProteinGoalG", 85.5));
        assertEquals(Arrays.asList(1,7), p.get("preferredWorkoutDays"));
        assertEquals(85.5, (Double)p.get("dailyProteinGoalG"), 0);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsContradictoryEquipment() {
        settings(10,3,Collections.emptyList(),Arrays.asList("none","chair"),Collections.emptyMap());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnsupportedEquipment() {
        settings(10,3,Collections.emptyList(),Arrays.asList("spaceship"),Collections.emptyMap());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidWeekday() {
        settings(10,3,Arrays.asList(0),Arrays.asList("none"),Collections.emptyMap());
    }
    @Test public void rejectsInvalidNumericInputs() {
        for (double n : new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0,20001}) {
            try { settings(10,3,Collections.emptyList(),Arrays.asList("none"),
                    Collections.singletonMap("dailyCalorieGoal",n)); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (int n : new int[]{-1,0,8}) {
            try { settings(10,n,Collections.emptyList(),Arrays.asList("none"),Collections.emptyMap()); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (int n : new int[]{-1,0,181}) {
            try { settings(n,3,Collections.emptyList(),Arrays.asList("none"),Collections.emptyMap()); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
