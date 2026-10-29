package com.example.fitnessbalance;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FoodNutrition {

    public static final String[] KEYS = {
            "energy_kcal",
            "protein_g",
            "carbohydrate_g",
            "fat_g",
            "fiber_g",
            "sodium_mg"
    };

    private FoodNutrition() {}

    public static boolean isPer100Grams(String basis) {
        if (basis == null) return false;

        String normalized = basis.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]", "");

        return normalized.equals("per100g")
                || normalized.equals("100g");
    }

    public static Double number(Object value) {
        if (!(value instanceof Number)) return null;

        double result = ((Number) value).doubleValue();

        return Double.isFinite(result) && result >= 0
                ? result : null;
    }

    public static Map<String, Object> scale(
            Map<String, Object> nutrition,
            double grams
    ) {
        if (!Double.isFinite(grams) || grams <= 0 || grams > 10000) {
            throw new IllegalArgumentException("Invalid portion");
        }

        Map<String, Object> result = new HashMap<>();

        for (String key : KEYS) {
            Double value = number(nutrition.get(key));

            if (value != null) {
                result.put(key, value * grams / 100.0);
            }
        }

        return result;
    }

    public static Map<String, Object> totals(
            List<Map<String, Object>> entries
    ) {
        Map<String, Object> totals = new HashMap<>();
        Map<String, Object> coverage = new HashMap<>();
        double calories = 0;

        for (String key : KEYS) {
            double sum = 0;
            int count = 0;

            for (Map<String, Object> entry : entries) {
                Object raw = entry.get("nutrients");

                Object value = raw instanceof Map
                        ? ((Map<?, ?>) raw).get(key) : null;

                // Support older entries that only stored calories.
                if (key.equals("energy_kcal") && value == null) {
                    value = entry.get("calories");
                }

                Double numeric = number(value);

                if (numeric != null) {
                    sum += numeric;
                    count++;
                }
            }

            if (count > 0) {
                totals.put(key, sum);
            }

            coverage.put(key, count);

            if (key.equals("energy_kcal")) {
                calories = sum;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCalories", Math.round(calories));
        result.put("nutrientTotals", totals);
        result.put("nutrientCoverage", coverage);

        return result;
    }

    public static String portionUnit(String basis) {
        if (basis == null) return null;

        String normalized = basis.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]", "");

        if (normalized.equals("per100g")
                || normalized.equals("100g")) {
            return "g";
        }

        if (normalized.equals("per100ml")
                || normalized.equals("100ml")) {
            return "ml";
        }

        return null;
    }

    public static double totalAmount(double amount, double servings) {
        double total = amount * servings;

        if (!Double.isFinite(amount)
                || amount <= 0
                || !Double.isFinite(servings)
                || servings <= 0
                || servings > 10000
                || !Double.isFinite(total)
                || total <= 0
                || total > 10000) {
            throw new IllegalArgumentException("Invalid serving amount");
        }

        return total;
    }

    public static Map<String, Object> servingNutrition(
            Map<String, Object> nutrition,
            String basis,
            String unit,
            double amount,
            double servings
    ) {
        String referenceUnit = portionUnit(basis);

        if (referenceUnit == null || !referenceUnit.equals(unit)) {
            throw new IllegalArgumentException(
                    "Unknown or mismatched reference unit"
            );
        }

        // Existing scale() applies the per-100 multiplier.
        // The unit check above prevents treating ml as grams.
        return scale(withEstimatedEnergy(nutrition), totalAmount(amount, servings));
    }

    /** Reference-value estimate only; servingNutrition still requires a known basis. */
    public static Map<String, Object> withEstimatedEnergy(Map<String, Object> nutrition) {
        Map<String, Object> result = new HashMap<>(nutrition);
        if (number(nutrition.get("energy_kcal")) != null) return result;
        Double protein = number(nutrition.get("protein_g"));
        Double carbs = number(nutrition.get("carbohydrate_g"));
        Double fat = number(nutrition.get("fat_g"));
        if (protein != null && carbs != null && fat != null) {
            double estimate = 4 * protein + 4 * carbs + 9 * fat;
            if (Double.isFinite(estimate)) result.put("energy_kcal", estimate);
        }
        return result;
    }

    public static boolean usesEstimatedEnergy(Map<String, Object> nutrition) {
        return number(nutrition.get("energy_kcal")) == null
                && number(withEstimatedEnergy(nutrition).get("energy_kcal")) != null;
    }

    public static Map<String, Object> manualNutrition(
            double calories,
            double servings
    ) {
        double total = calories * servings;

        if (!Double.isFinite(calories)
                || calories < 0
                || !Double.isFinite(servings)
                || servings <= 0
                || servings > 10000
                || !Double.isFinite(total)) {
            throw new IllegalArgumentException("Invalid manual calories");
        }

        Map<String, Object> result = new HashMap<>();

        // Never invent other nutrients from manual calories.
        result.put("energy_kcal", total);

        return result;
    }
}
