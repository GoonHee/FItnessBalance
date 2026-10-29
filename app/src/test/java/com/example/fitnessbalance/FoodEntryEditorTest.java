package com.example.fitnessbalance;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FoodEntryEditorTest {
    private Map<String, Object> entry() {
        Map<String, Object> e = new HashMap<>();
        e.put("entryId", "one"); e.put("totalAmount", 100.0);
        e.put("portionUnit", "g"); e.put("servingAmount", 100.0);
        e.put("servings", 1.0); e.put("calories", 200.0);
        Map<String, Object> n = new HashMap<>();
        n.put("energy_kcal", 200.0); n.put("protein_g", 10.0);
        n.put("sodium_mg", 0.0); e.put("nutrients", n);
        e.put("sourceDatabase", "saved source");
        return e;
    }
    @Test public void scalesSnapshotAndPreservesMissingAndZero() {
        Map<String, Object> original = entry();
        Map<String, Object> updated = FoodEntryEditor.resize(original, 30, 2, "Lunch", 0);
        Map<?, ?> nutrients = (Map<?, ?>) updated.get("nutrients");
        assertEquals(120.0, (Double) updated.get("calories"), 0.00001);
        assertEquals(6.0, (Double) nutrients.get("protein_g"), 0.00001);
        assertEquals(0.0, (Double) nutrients.get("sodium_mg"), 0);
        assertFalse(nutrients.containsKey("fat_g"));
        assertEquals("saved source", updated.get("sourceDatabase"));
        assertEquals(200.0, (Double) original.get("calories"), 0);
    }
    @Test public void manualCaloriesFollowServingsNotGrams() {
        Map<String, Object> original = entry(); original.put("calculationMode", "manual");
        Map<String, Object> updated = FoodEntryEditor.resize(original, 50, 3, "Dinner", 80);
        assertEquals(240.0, (Double) updated.get("calories"), 0);
        assertEquals(1, ((Map<?, ?>) updated.get("nutrients")).size());
    }
    @Test public void replacesOnlyTargetAndRecalculatesTotals() {
        Map<String, Object> original = entry();
        Map<String, Object> other = entry(); other.put("entryId", "two");
        List<Map<String, Object>> items = new ArrayList<>(Arrays.asList(original, other));
        Map<String, Object> updated = FoodEntryEditor.resize(original, 50, 1, "Lunch", 0);
        FoodEntryEditor.replace(items, original, updated);
        assertEquals(2, items.size()); assertSame(other, items.get(1));
        assertEquals(300L, FoodNutrition.totals(items).get("totalCalories"));
        // An acknowledgement retry must not silently overwrite a newer version.
        try { FoodEntryEditor.replace(items, original, updated); fail(); }
        catch (IllegalStateException expected) { }
    }
    @Test(expected=IllegalStateException.class) public void deletedEntryIsNotRecreated() {
        FoodEntryEditor.replace(new ArrayList<>(), entry(), entry());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidPortion() {
        FoodEntryEditor.resize(entry(), Double.NaN, 1, "Lunch", 0);
    }
    @Test(expected=IllegalArgumentException.class) public void refusesLegacyWithoutReference() {
        Map<String, Object> e = entry(); e.remove("totalAmount");
        FoodEntryEditor.resize(e, 50, 1, "Lunch", 0);
    }
}
