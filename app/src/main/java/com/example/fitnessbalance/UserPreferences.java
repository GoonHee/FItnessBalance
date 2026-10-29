package com.example.fitnessbalance;

import java.util.*;

/** Validated user choices, not calculated dietary prescriptions. */
final class UserPreferences {
    static final String[] GOALS = {"dailyCalorieGoal", "dailyProteinGoalG",
            "dailyCarbohydrateGoalG", "dailyFatGoalG"};
    static final List<String> EQUIPMENT = Arrays.asList("none", "chair", "mat", "dumbbells", "resistance_band");
    static final List<String> DIETS = Arrays.asList("halal", "vegetarian", "vegan", "avoid_pork", "avoid_alcohol");
    private UserPreferences() {}

    static Map<String, Object> validate(int minutes, int target, List<Integer> days,
            List<String> equipment, List<String> diets, Map<String, Double> goals) {
        if (minutes < 1 || minutes > 180 || target < 1 || target > 7)
            throw new IllegalArgumentException("Invalid workout preferences");
        for (Integer day : days) if (day == null || day < 1 || day > 7)
            throw new IllegalArgumentException("Invalid workout day");
        if (equipment.isEmpty() || !EQUIPMENT.containsAll(equipment)
                || (equipment.contains("none") && equipment.size() > 1))
            throw new IllegalArgumentException("Select equipment or no equipment");
        if (!DIETS.containsAll(diets)) throw new IllegalArgumentException("Invalid dietary preference");
        Map<String, Object> values = new HashMap<>();
        values.put("preferredWorkoutMinutes", minutes);
        values.put("weeklyWorkoutTarget", target);
        values.put("preferredWorkoutDays", new ArrayList<>(new LinkedHashSet<>(days)));
        values.put("equipment", new ArrayList<>(new LinkedHashSet<>(equipment)));
        values.put("dietaryPreferences", new ArrayList<>(new LinkedHashSet<>(diets)));
        for (String key : GOALS) {
            Double value = goals.get(key);
            if (value != null) {
                double max = key.equals("dailyCalorieGoal") ? 20000 : 5000;
                if (!Double.isFinite(value) || value < 0 || value > max
                        || (key.equals("dailyCalorieGoal") && value == 0))
                    throw new IllegalArgumentException("Invalid nutrition goal");
                values.put(key, value);
            }
        }
        // Bounds reject input mistakes; they do not imply medically appropriate goals.
        return values;
    }
}
