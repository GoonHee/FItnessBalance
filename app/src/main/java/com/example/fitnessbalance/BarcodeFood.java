package com.example.fitnessbalance;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BarcodeFood {

    private BarcodeFood() {}

    public static boolean validBarcode(String code) {
        return code != null
                && code.matches("(?:[0-9]{8}|[0-9]{12,14})");
    }

    private static Double numeric(
            JSONObject object,
            String field
    ) {
        Object raw = object.opt(field);

        if (raw == null
                || raw == JSONObject.NULL
                || raw instanceof Boolean) {
            return null;
        }

        try {
            double value = raw instanceof Number
                    ? ((Number) raw).doubleValue()
                    : Double.parseDouble(raw.toString());

            return Double.isFinite(value) && value >= 0
                    ? value : null;

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double reported(
            JSONObject nutrients,
            String nutrient
    ) {
        String modifier = nutrients.optString(
                nutrient + "_modifier",
                ""
        );

        // Do not treat a bound such as "<0.5" as an exact value.
        if (modifier.contains("<") || modifier.contains(">")) {
            return null;
        }

        return numeric(nutrients, nutrient + "_100g");
    }

    private static void put(
            Map<String, Object> target,
            String key,
            Double value,
            double factor
    ) {
        if (value != null
                && Double.isFinite(value * factor)) {
            target.put(key, value * factor);
        }
    }

    public static Map<String, Object> parse(
            String json,
            String barcode
    ) throws JSONException {

        if (!validBarcode(barcode)) {
            throw new IllegalArgumentException(
                    "Invalid product barcode"
            );
        }

        JSONObject root = new JSONObject(json);

        if (root.optInt("status", -1) == 0) {
            return null;
        }

        if (root.optInt("status", -1) != 1
                || root.optJSONObject("product") == null) {
            throw new JSONException("Invalid product response");
        }

        JSONObject product = root.getJSONObject("product");

        String returnedCode = product.optString(
                "code",
                root.optString("code", barcode)
        );

        // Equivalent UPC/EAN identifiers can differ in zero-padding.
        if (!validBarcode(returnedCode)
                || !barcode.replaceFirst("^0+(?!$)", "")
                .equals(returnedCode.replaceFirst("^0+(?!$)", ""))) {
            throw new JSONException("Barcode identity mismatch");
        }

        String name = product.optString(
                "product_name", ""
        ).trim();

        if (name.isEmpty()) {
            name = product.optString(
                    "product_name_en", ""
            ).trim();
        }

        if (name.isEmpty()) {
            name = product.optString(
                    "generic_name", ""
            ).trim();
        }

        if (name.isEmpty()) {
            name = "Product " + barcode;
        }

        Map<String, Object> food = new HashMap<>();

        food.put("food_name", name);
        food.put(
                "food_name_lowercase",
                name.toLowerCase(Locale.ROOT)
        );
        food.put("food_key", "off_" + barcode);
        food.put("barcode", barcode);
        food.put("source_database", "Open Food Facts");
        food.put(
                "source_url",
                "https://world.openfoodfacts.org/product/" + barcode
        );
        food.put("retrieved_at", new java.util.Date());

        /*
         * Open Food Facts uses "_100g" nutrient keys for both
         * weight-based and volume-based products.
         * Ask the user to confirm the package reference unit.
         */
        food.put("nutrition_basis", "unknown");
        food.put("basis_confirmation_required", true);
        food.put(
                "source_nutrition_data_per",
                product.optString("nutrition_data_per", "")
        );

        Map<String, Object> nutrition = new HashMap<>();

        JSONObject n = product.optJSONObject("nutriments");

        boolean nutritionAvailable =
                !"off".equals(product.optString(
                        "nutrition_data", ""
                ))
                        && !"on".equals(product.optString(
                        "no_nutrition_data", ""
                ));

        if (n != null && nutritionAvailable) {
            Double kcal = reported(n, "energy-kcal");

            if (kcal == null) {
                Double kj = reported(n, "energy-kj");

                if (kj == null) {
                    // The API standardizes energy_100g to kJ.
                    kj = reported(n, "energy");
                }

                if (kj != null) {
                    kcal = kj / 4.184;
                }
            }

            put(nutrition, "energy_kcal", kcal, 1);
            put(nutrition, "protein_g",
                    reported(n, "proteins"), 1);
            put(nutrition, "carbohydrate_g",
                    reported(n, "carbohydrates"), 1);
            put(nutrition, "fat_g",
                    reported(n, "fat"), 1);
            put(nutrition, "fiber_g",
                    reported(n, "fiber"), 1);

            /*
             * Standardized sodium_100g is in grams,
             * regardless of the original label-input unit.
             */
            put(nutrition, "sodium_mg",
                    reported(n, "sodium"), 1000);
        }

        food.put("nutrition", nutrition);

        return food;
    }
}