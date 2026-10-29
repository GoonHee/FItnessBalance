package com.example.fitnessbalance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Edits the historical snapshot without rereading mutable catalogue values. */
final class FoodEntryEditor {
    private FoodEntryEditor() {}

    static Map<String, Object> resize(Map<String, Object> original,
            double amount, double servings, String meal, double manualCalories) {
        double total = FoodNutrition.totalAmount(amount, servings);
        Double oldTotal = FoodNutrition.number(original.get("totalAmount"));
        if (!(original.get("entryId") instanceof String) || oldTotal == null
                || oldTotal <= 0 || !(original.get("nutrients") instanceof Map)) {
            throw new IllegalArgumentException("This older entry has no editable serving snapshot.");
        }
        Map<String, Object> result = new HashMap<>(original);
        Map<String, Object> nutrients = new HashMap<>();
        if ("manual".equals(original.get("calculationMode"))) {
            nutrients.putAll(FoodNutrition.manualNutrition(manualCalories, servings));
            result.put("manualCaloriesPerServing", manualCalories);
        } else {
            Map<?, ?> saved = (Map<?, ?>) original.get("nutrients");
            for (Map.Entry<?, ?> item : saved.entrySet()) {
                Double value = FoodNutrition.number(item.getValue());
                if (item.getKey() instanceof String && value != null) {
                    double scaled = value * (total / oldTotal);
                    if (!Double.isFinite(scaled)) throw new IllegalArgumentException("Invalid nutrition total");
                    nutrients.put((String) item.getKey(), scaled);
                }
            }
        }
        if (FoodNutrition.number(nutrients.get("energy_kcal")) == null) {
            throw new IllegalArgumentException("Saved calories are unavailable.");
        }
        result.put("servingAmount", amount);
        result.put("servings", servings);
        result.put("totalAmount", total);
        result.put("meal", meal);
        Object unit = original.get("portionUnit");
        if (!"g".equals(unit) && !"ml".equals(unit)) {
            throw new IllegalArgumentException("Saved portion unit is unavailable.");
        }
        result.put("g".equals(unit) ? "portionGrams" : "portionMillilitres", total);
        result.put("nutrients", nutrients);
        result.put("calories", nutrients.get("energy_kcal"));
        return result;
    }

    static void replace(List<Map<String, Object>> items,
            Map<String, Object> original, Map<String, Object> replacement) {
        Object id = original.get("entryId");
        if (!(id instanceof String) || !id.equals(replacement.get("entryId"))) {
            throw new IllegalArgumentException("Invalid entry ID");
        }
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).get("entryId"))) {
                if (!original.equals(items.get(i))) {
                    throw new IllegalStateException("This entry changed. Close the editor and reopen it.");
                }
                items.set(i, replacement);
                return;
            }
        }
        throw new IllegalStateException("This entry was removed. Close the editor and refresh the log.");
    }
}
