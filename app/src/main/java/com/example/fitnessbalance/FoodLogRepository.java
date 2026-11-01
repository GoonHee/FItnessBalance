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

            writeTotals(transaction, log, daily, items, date);

            return null;
        });
    }

    static Task<Void> saveMeal(String userId, String date, String mealId,
            String operation, List<Map<String,Object>> additions) {
        if(!FoodUi.validDate(date))throw new IllegalArgumentException("Invalid date.");
        java.util.UUID.fromString(operation);
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        DocumentReference user=db.collection("users").document(userId);
        DocumentReference log=user.collection("foodLogs").document(date), daily=user.collection("dailyData").document(date),
            receipt=user.collection("savedMealLogs").document(operation);
        for(Map<String,Object> addition:additions)addition.put("loggedAt",new java.util.Date());
        return db.runTransaction(tx->{
            DocumentSnapshot done=tx.get(receipt);
            if(done.exists()){
                if(!date.equals(done.getString("date"))||!mealId.equals(done.getString("mealId")))throw new IllegalStateException("Operation changed.");
                return null;
            }
            List<Map<String,Object>> items=entries(tx.get(log));
            SavedMealService.append(items,additions);
            writeTotals(tx,log,daily,items,date);
            Map<String,Object> ack=new HashMap<>();ack.put("date",date);ack.put("mealId",mealId);
            ack.put("entryCount",additions.size());ack.put("createdAt",FieldValue.serverTimestamp());tx.set(receipt,ack);
            return null;
        });
    }
    private static void writeTotals(com.google.firebase.firestore.Transaction transaction,
            DocumentReference log,DocumentReference daily,List<Map<String,Object>> items,String date) {
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

    }
}