package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalorieCalculatorActivity extends AppCompatActivity {

    // UI Components
    private ImageButton backButton;
    private Spinner foodCategorySpinner, foodItemSpinner, portionUnitSpinner, exerciseSpinner;
    private TextInputEditText portionSizeInput, exerciseDurationInput, userWeightInput;
    private Button calculateFoodCaloriesButton, calculateExerciseCaloriesButton;
    private Button addToFoodLogButton, addToExerciseLogButton;
    private LinearLayout foodResultLayout, exerciseResultLayout;
    private TextView foodCalculationText, foodCaloriesResult;
    private TextView exerciseCalculationText, exerciseCaloriesResult;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // Calculator data
    private Map<String, List<FoodItem>> foodDatabase;
    private Map<String, ExerciseItem> exerciseDatabase;
    private double userWeight = 70.0; // Default weight

    // Current calculations
    private double currentFoodCalories = 0;
    private String currentFoodName = "";
    private double currentExerciseCalories = 0;
    private String currentExerciseName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calorie_calculator);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();
        initializeFoodDatabase();
        initializeExerciseDatabase();
        setupSpinners();
        loadUserWeight();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);

        // Food calculator components
        foodCategorySpinner = findViewById(R.id.foodCategorySpinner);
        foodItemSpinner = findViewById(R.id.foodItemSpinner);
        portionUnitSpinner = findViewById(R.id.portionUnitSpinner);
        portionSizeInput = findViewById(R.id.portionSizeInput);
        calculateFoodCaloriesButton = findViewById(R.id.calculateFoodCaloriesButton);
        foodResultLayout = findViewById(R.id.foodResultLayout);
        foodCalculationText = findViewById(R.id.foodCalculationText);
        foodCaloriesResult = findViewById(R.id.foodCaloriesResult);
        addToFoodLogButton = findViewById(R.id.addToFoodLogButton);

        // Exercise calculator components
        exerciseSpinner = findViewById(R.id.exerciseSpinner);
        exerciseDurationInput = findViewById(R.id.exerciseDurationInput);
        userWeightInput = findViewById(R.id.userWeightInput);
        calculateExerciseCaloriesButton = findViewById(R.id.calculateExerciseCaloriesButton);
        exerciseResultLayout = findViewById(R.id.exerciseResultLayout);
        exerciseCalculationText = findViewById(R.id.exerciseCalculationText);
        exerciseCaloriesResult = findViewById(R.id.exerciseCaloriesResult);
        addToExerciseLogButton = findViewById(R.id.addToExerciseLogButton);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        calculateFoodCaloriesButton.setOnClickListener(v -> calculateFoodCalories());
        calculateExerciseCaloriesButton.setOnClickListener(v -> calculateExerciseCalories());
        addToFoodLogButton.setOnClickListener(v -> addToFoodLog());
        addToExerciseLogButton.setOnClickListener(v -> addToExerciseLog());
    }

    private void initializeFoodDatabase() {
        foodDatabase = new HashMap<>();

        // Fruits
        List<FoodItem> fruits = Arrays.asList(
                new FoodItem("Apple", 52, "medium", 182),
                new FoodItem("Banana", 89, "medium", 118),
                new FoodItem("Orange", 47, "medium", 131),
                new FoodItem("Strawberries", 32, "cup", 152),
                new FoodItem("Grapes", 69, "cup", 92)
        );
        foodDatabase.put("Fruits", fruits);

        // Vegetables
        List<FoodItem> vegetables = Arrays.asList(
                new FoodItem("Broccoli", 34, "cup", 91),
                new FoodItem("Carrot", 41, "medium", 61),
                new FoodItem("Spinach", 7, "cup", 30),
                new FoodItem("Potato", 163, "medium", 213),
                new FoodItem("Tomato", 22, "medium", 123)
        );
        foodDatabase.put("Vegetables", vegetables);

        // Proteins
        List<FoodItem> proteins = Arrays.asList(
                new FoodItem("Chicken Breast", 165, "100g", 100),
                new FoodItem("Salmon", 206, "100g", 100),
                new FoodItem("Eggs", 78, "large", 50),
                new FoodItem("Tofu", 76, "100g", 100),
                new FoodItem("Beef Steak", 271, "100g", 100)
        );
        foodDatabase.put("Proteins", proteins);

        // Carbohydrates
        List<FoodItem> carbs = Arrays.asList(
                new FoodItem("White Rice", 130, "cup", 158),
                new FoodItem("Brown Rice", 112, "cup", 158),
                new FoodItem("Pasta", 131, "cup", 107),
                new FoodItem("Bread", 79, "slice", 28),
                new FoodItem("Oatmeal", 166, "cup", 234)
        );
        foodDatabase.put("Carbohydrates", carbs);

        // Dairy
        List<FoodItem> dairy = Arrays.asList(
                new FoodItem("Milk", 103, "cup", 244),
                new FoodItem("Yogurt", 149, "cup", 245),
                new FoodItem("Cheese", 113, "slice", 28),
                new FoodItem("Butter", 102, "tbsp", 14)
        );
        foodDatabase.put("Dairy", dairy);
    }

    private void initializeExerciseDatabase() {
        exerciseDatabase = new HashMap<>();

        // MET values (Metabolic Equivalent of Task) - calories burned per kg per hour
        exerciseDatabase.put("Running", new ExerciseItem("Running", 7.0, "moderate pace"));
        exerciseDatabase.put("Cycling", new ExerciseItem("Cycling", 6.0, "moderate pace"));
        exerciseDatabase.put("Swimming", new ExerciseItem("Swimming", 5.8, "moderate pace"));
        exerciseDatabase.put("Walking", new ExerciseItem("Walking", 3.5, "brisk pace"));
        exerciseDatabase.put("Weight Lifting", new ExerciseItem("Weight Lifting", 3.0, "general"));
        exerciseDatabase.put("Yoga", new ExerciseItem("Yoga", 2.5, "hatha"));
        exerciseDatabase.put("Basketball", new ExerciseItem("Basketball", 6.5, "general"));
        exerciseDatabase.put("Soccer", new ExerciseItem("Soccer", 7.0, "general"));
        exerciseDatabase.put("Tennis", new ExerciseItem("Tennis", 5.0, "general"));
        exerciseDatabase.put("HIIT", new ExerciseItem("HIIT", 8.0, "high intensity"));
        exerciseDatabase.put("Dancing", new ExerciseItem("Dancing", 4.5, "aerobic"));
        exerciseDatabase.put("Hiking", new ExerciseItem("Hiking", 4.5, "with pack"));
    }

    private void setupSpinners() {
        // Food category spinner
        List<String> categories = new ArrayList<>(foodDatabase.keySet());
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodCategorySpinner.setAdapter(categoryAdapter);

        // Update food items when category changes
        foodCategorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateFoodItemsSpinner();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Portion unit spinner
        List<String> portionUnits = Arrays.asList("pieces", "cups", "tbsp", "grams", "ounces");
        ArrayAdapter<String> portionUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, portionUnits);
        portionUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        portionUnitSpinner.setAdapter(portionUnitAdapter);

        // Exercise spinner
        List<String> exercises = new ArrayList<>(exerciseDatabase.keySet());
        ArrayAdapter<String> exerciseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, exercises);
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseSpinner.setAdapter(exerciseAdapter);

        // Initial food items setup
        updateFoodItemsSpinner();
    }

    private void updateFoodItemsSpinner() {
        String selectedCategory = foodCategorySpinner.getSelectedItem().toString();
        List<FoodItem> foodItems = foodDatabase.get(selectedCategory);

        List<String> foodNames = new ArrayList<>();
        for (FoodItem item : foodItems) {
            foodNames.add(item.name);
        }

        ArrayAdapter<String> foodItemAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, foodNames);
        foodItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodItemSpinner.setAdapter(foodItemAdapter);
    }

    private void loadUserWeight() {
        if (currentUser != null) {
            String userId = currentUser.getUid();
            firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.contains("weight")) {
                            userWeight = documentSnapshot.getDouble("weight");
                            if (userWeight > 0) {
                                userWeightInput.setText(String.valueOf(userWeight));
                            }
                        }
                    });
        }
    }

    private void calculateFoodCalories() {
        String portionSizeStr = portionSizeInput.getText().toString().trim();

        if (TextUtils.isEmpty(portionSizeStr)) {
            portionSizeInput.setError("Enter portion size");
            return;
        }

        try {
            double portionSize = Double.parseDouble(portionSizeStr);
            if (portionSize <= 0) {
                portionSizeInput.setError("Portion size must be positive");
                return;
            }

            String selectedCategory = foodCategorySpinner.getSelectedItem().toString();
            String selectedFood = foodItemSpinner.getSelectedItem().toString();
            String portionUnit = portionUnitSpinner.getSelectedItem().toString();

            // Find the selected food item
            List<FoodItem> foodItems = foodDatabase.get(selectedCategory);
            FoodItem selectedItem = null;
            for (FoodItem item : foodItems) {
                if (item.name.equals(selectedFood)) {
                    selectedItem = item;
                    break;
                }
            }

            if (selectedItem != null) {
                // For simplicity, we'll assume 1 portion = standard serving size
                // In a real app, you'd have conversion factors between different units
                currentFoodCalories = selectedItem.caloriesPerServing * portionSize;
                currentFoodName = selectedFood;

                // Show result
                foodCalculationText.setText(String.format(Locale.getDefault(),
                        "%s: %.1f %s of %s", selectedFood, portionSize, portionUnit, selectedItem.standardUnit));
                foodCaloriesResult.setText(String.format(Locale.getDefault(), "%.0f calories", currentFoodCalories));
                foodResultLayout.setVisibility(View.VISIBLE);
            }

        } catch (NumberFormatException e) {
            portionSizeInput.setError("Invalid portion size");
        }
    }

    private void calculateExerciseCalories() {
        String durationStr = exerciseDurationInput.getText().toString().trim();
        String weightStr = userWeightInput.getText().toString().trim();

        if (TextUtils.isEmpty(durationStr)) {
            exerciseDurationInput.setError("Enter duration");
            return;
        }

        try {
            double duration = Double.parseDouble(durationStr);
            if (duration <= 0) {
                exerciseDurationInput.setError("Duration must be positive");
                return;
            }

            // Use user weight if provided, otherwise use default
            double weight = userWeight;
            if (!TextUtils.isEmpty(weightStr)) {
                weight = Double.parseDouble(weightStr);
                if (weight <= 0) {
                    userWeightInput.setError("Weight must be positive");
                    return;
                }
            }

            String selectedExercise = exerciseSpinner.getSelectedItem().toString();
            ExerciseItem exercise = exerciseDatabase.get(selectedExercise);

            if (exercise != null) {
                // Calculate calories burned: MET * weight * time(hours)
                double hours = duration / 60.0;
                currentExerciseCalories = exercise.metValue * weight * hours;
                currentExerciseName = selectedExercise;

                // Show result
                exerciseCalculationText.setText(String.format(Locale.getDefault(),
                        "%s for %.0f minutes (%.1f kg)", selectedExercise, duration, weight));
                exerciseCaloriesResult.setText(String.format(Locale.getDefault(), "%.0f calories burned", currentExerciseCalories));
                exerciseResultLayout.setVisibility(View.VISIBLE);
            }

        } catch (NumberFormatException e) {
            exerciseDurationInput.setError("Invalid duration");
        }
    }

    private void addToFoodLog() {
        if (currentFoodCalories > 0) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("foodName", currentFoodName);
            resultIntent.putExtra("calories", (int) currentFoodCalories);
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Calculate calories first", Toast.LENGTH_SHORT).show();
        }
    }

    private void addToExerciseLog() {
        if (currentExerciseCalories > 0) {
            // Here you would integrate with your exercise logging system
            Toast.makeText(this,
                    String.format(Locale.getDefault(), "Added: %.0f calories burned from %s",
                            currentExerciseCalories, currentExerciseName),
                    Toast.LENGTH_LONG).show();

            // You can implement exercise logging similar to food logging
            saveExerciseToLog();
        } else {
            Toast.makeText(this, "Calculate calories first", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveExerciseToLog() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        String today = java.text.DateFormat.getDateInstance().format(new java.util.Date());

        Map<String, Object> exerciseLog = new HashMap<>();
        exerciseLog.put("exercise", currentExerciseName);
        exerciseLog.put("caloriesBurned", (int) currentExerciseCalories);
        exerciseLog.put("duration", exerciseDurationInput.getText().toString());
        exerciseLog.put("date", today);
        exerciseLog.put("timestamp", new java.util.Date());

        firestore.collection("users").document(userId)
                .collection("exerciseLogs")
                .add(exerciseLog)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Exercise logged successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error logging exercise", Toast.LENGTH_SHORT).show();
                });
    }

    // Food item class
    private static class FoodItem {
        String name;
        double caloriesPerServing;
        String standardUnit;
        double servingSizeGrams;

        FoodItem(String name, double caloriesPerServing, String standardUnit, double servingSizeGrams) {
            this.name = name;
            this.caloriesPerServing = caloriesPerServing;
            this.standardUnit = standardUnit;
            this.servingSizeGrams = servingSizeGrams;
        }
    }

    // Exercise item class
    private static class ExerciseItem {
        String name;
        double metValue;
        String intensity;

        ExerciseItem(String name, double metValue, String intensity) {
            this.name = name;
            this.metValue = metValue;
            this.intensity = intensity;
        }
    }
}