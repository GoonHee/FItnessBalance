package com.example.fitnessbalance;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class CalorieFallbackTest {
    private Map<String, Object> macros() {
        Map<String, Object> n = new HashMap<>();
        n.put("protein_g", 10.0); n.put("carbohydrate_g", 20.0); n.put("fat_g", 5.0);
        return n;
    }
    @Test public void estimatesThenScalesWithoutChangingSource() {
        Map<String, Object> n = macros();
        Map<String, Object> scaled = FoodNutrition.servingNutrition(n, "per_100g", "g", 30, 2);
        assertEquals(99.0, (Double) scaled.get("energy_kcal"), 0.00001);
        assertTrue(FoodNutrition.usesEstimatedEnergy(n));
        assertFalse(n.containsKey("energy_kcal"));
        assertFalse(scaled.containsKey("fiber_g"));
    }
    @Test public void sourceCaloriesIncludingZeroTakePriority() {
        Map<String, Object> n = macros(); n.put("energy_kcal", 0.0);
        assertFalse(FoodNutrition.usesEstimatedEnergy(n));
        assertEquals(0.0, (Double) FoodNutrition.withEstimatedEnergy(n).get("energy_kcal"), 0);
        n.put("energy_kcal", 180.0);
        assertEquals(180.0, (Double) FoodNutrition.withEstimatedEnergy(n).get("energy_kcal"), 0);
    }
    @Test public void neverTreatsMissingMacroAsZero() {
        for (String key : new String[]{"protein_g", "carbohydrate_g", "fat_g"}) {
            Map<String, Object> n = macros(); n.remove(key);
            assertNull(FoodNutrition.withEstimatedEnergy(n).get("energy_kcal"));
            assertFalse(FoodNutrition.usesEstimatedEnergy(n));
        }
    }
    @Test public void rejectsInvalidOrOverflowingMacros() {
        for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            Map<String, Object> n = macros(); n.put("protein_g", value);
            assertFalse(FoodNutrition.usesEstimatedEnergy(n));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void unknownBasisStillBlocksScaling() {
        FoodNutrition.servingNutrition(macros(), "unknown", "g", 100, 1);
    }
    @Test(expected=IllegalArgumentException.class) public void cannotConvertVolumeToMass() {
        FoodNutrition.servingNutrition(macros(), "per_100g", "ml", 100, 1);
    }
    @Test public void volumeBasisAndManualFallbackWorkIndependently() {
        assertEquals(330.0, (Double) FoodNutrition.servingNutrition(
                macros(), "per_100ml", "ml", 100, 2).get("energy_kcal"), 0);
        Map<String, Object> manual = FoodNutrition.manualNutrition(80, 2);
        assertEquals(160.0, (Double) manual.get("energy_kcal"), 0);
        assertEquals(1, manual.size());
    }
}
