package com.example.fitnessbalance;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class UserBarcodeFood {

    private UserBarcodeFood() {}

    public static Map<String, Object> create(
            String barcode,
            String name,
            double reference,
            String unit,
            Map<String, Object> labelValues
    ) {
        if (!BarcodeFood.validBarcode(barcode)
                || name == null
                || name.trim().isEmpty()
                || name.trim().length() > 200
                || (!"g".equals(unit) && !"ml".equals(unit))) {
            throw new IllegalArgumentException("Invalid product");
        }

        FoodNutrition.totalAmount(reference, 1);

        if (FoodNutrition.number(
                labelValues.get("energy_kcal")
        ) == null) {
            throw new IllegalArgumentException("Calories required");
        }

        Map<String, Object> normalized = new HashMap<>();
        Map<String, Object> label = new HashMap<>();

        for (String key : FoodNutrition.KEYS) {
            if (!labelValues.containsKey(key)) continue;

            Double value = FoodNutrition.number(
                    labelValues.get(key)
            );

            if (value == null
                    || !Double.isFinite(
                    value * 100.0 / reference
            )) {
                throw new IllegalArgumentException(
                        "Invalid nutrient value"
                );
            }

            label.put(key, value);
            normalized.put(key, value * 100.0 / reference);
        }

        Map<String, Object> food = new HashMap<>();

        food.put("food_key", "user_barcode_" + barcode);
        food.put("barcode", barcode);
        food.put("food_name", name.trim());
        food.put(
                "food_name_lowercase",
                name.trim().toLowerCase(Locale.ROOT)
        );

        food.put(
                "source_database",
                "Your saved product (user entered)"
        );

        food.put("nutrition_basis", "per_100" + unit);
        food.put("portion_unit", unit);
        food.put("nutrition", normalized);

        // Preserve exactly what the user copied from the label.
        food.put("label_amount", reference);
        food.put("label_unit", unit);
        food.put("label_nutrition", label);

        food.put("nutrition_verification", "user_entered");
        food.put("basis_confirmation_required", false);

        return food;
    }
}