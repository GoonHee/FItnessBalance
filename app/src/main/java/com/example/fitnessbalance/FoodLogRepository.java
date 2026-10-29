package com.example.fitnessbalance;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FoodLogRepository {

    private FoodLogRepository() {}

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> entries(
            DocumentSnapshot document
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = document.get("foodItems");

        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) {
                    throw new IllegalStateException(
                            "Invalid food log entry"
                    );
                }

                result.add(new HashMap<>(
                        (Map<String, Object>) item
                ));
            }
        } else if (raw != null) {
            throw new IllegalStateException(
                    "Invalid food log format"
            );
        }

        return result;
    }

    static Task<Void> save(
            String userId,
            String date,
            Map<String, Object> entry,
            boolean remove
    ) {
        return mutate(userId, date, entry, remove, null);
    }

    static Task<Void> update(String userId, String date,
            Map<String, Object> original, Map<String, Object> replacement) {
        return mutate(userId, date, replacement, false, original);
    }

    private static Task<Void> mutate(String userId, String date,
            Map<String, Object> entry, boolean remove,
            Map<String, Object> original) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference log = db.collection("users")
                .document(userId)
                .collection("foodLogs")
                .document(date);

        DocumentReference daily = db.collection("users")
                .document(userId)
                .collection("dailyData")
                .document(date);

        return db.runTransaction(transaction -> {
            List<Map<String, Object>> items =
                    entries(transaction.get(log));

            Object id = entry.get("entryId");

            if (original != null) {
                FoodEntryEditor.replace(items, original, entry);
            } else if (remove) {
                for (int i = 0; i < items.size(); i++) {
                    boolean matches = id != null
                            ? id.equals(items.get(i).get("entryId"))
                            : entry.equals(items.get(i));

                    if (matches) {
                        items.remove(i);
                        break;
                    }
                }
            } else {
                if (!(id instanceof String)) {
                    throw new IllegalArgumentException("Invalid entry ID");
                }
                // Reusing an entry ID prevents duplicate retry saves.
                boolean exists = false;

                for (Map<String, Object> item : items) {
                    if (id.equals(item.get("entryId"))) {
                        exists = true;
                    }
                }

                if (!exists) {
                    items.add(entry);
                }
            }

            Map<String, Object> values =
                    FoodNutrition.totals(items);

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put(
                    "caloriesConsumed",
                    values.get("totalCalories")
            );
            dashboard.put(
                    "nutrientTotals",
                    values.get("nutrientTotals")
            );
            dashboard.put(
                    "nutrientCoverage",
                    values.get("nutrientCoverage")
            );
            dashboard.put("foodEntryCount", items.size());
            dashboard.put(
                    "lastUpdated",
                    FieldValue.serverTimestamp()
            );

            values.put("foodItems", items);
            values.put("date", date);
            values.put("timestamp", FieldValue.serverTimestamp());

            // Replace complete nutrient maps to clear obsolete totals.
            transaction.set(
                    log,
                    values,
                    SetOptions.mergeFields(
                            "foodItems",
                            "date",
                            "totalCalories",
                            "nutrientTotals",
                            "nutrientCoverage",
                            "timestamp"
                    )
            );

            // Preserve unrelated fields, including exercise totals.
            transaction.set(
                    daily,
                    dashboard,
                    SetOptions.mergeFields(
                            "caloriesConsumed",
                            "nutrientTotals",
                            "nutrientCoverage",
                            "foodEntryCount",
                            "lastUpdated"
                    )
            );

            return null;
        });
    }
}