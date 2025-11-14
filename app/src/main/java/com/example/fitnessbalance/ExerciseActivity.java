package com.example.fitnessbalance;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExerciseActivity extends AppCompatActivity {

    // UI Components
    private ImageButton backButton;
    private TextView dateText, suggestedExercisesText, totalCaloriesBurnedText;
    private FlexboxLayout suggestedExercisesLayout;
    private Spinner exerciseSpinner;
    private TextInputEditText durationInput;
    private Button calculateCaloriesButton, addExerciseButton, saveExercisesButton;
    private LinearLayout caloriesResultLayout, exercisesListLayout;
    private TextView caloriesResultText;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // Exercise data
    private List<ExerciseItem> todayExercises;
    private int totalCaloriesBurned = 0;
    private double userWeight = 70.0; // Default weight
    private List<String> userPreferredExercises;

    // Current exercise calculation
    private String currentExerciseName = "";
    private int currentExerciseDuration = 0;
    private int currentExerciseCalories = 0;

    // Date
    private String currentDate;

    // Exercise database
    private Map<String, ExerciseData> exerciseDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();
        initializeExerciseDatabase();
        updateDate();
        loadUserData();
        loadTodayExercises();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        dateText = findViewById(R.id.dateText);
        suggestedExercisesText = findViewById(R.id.suggestedExercisesText);
        suggestedExercisesLayout = findViewById(R.id.suggestedExercisesLayout);
        exerciseSpinner = findViewById(R.id.exerciseSpinner);
        durationInput = findViewById(R.id.durationInput);
        calculateCaloriesButton = findViewById(R.id.calculateCaloriesButton);
        caloriesResultLayout = findViewById(R.id.caloriesResultLayout);
        caloriesResultText = findViewById(R.id.caloriesResultText);
        addExerciseButton = findViewById(R.id.addExerciseButton);
        exercisesListLayout = findViewById(R.id.exercisesListLayout);
        totalCaloriesBurnedText = findViewById(R.id.totalCaloriesBurnedText);
        saveExercisesButton = findViewById(R.id.saveExercisesButton);

        todayExercises = new ArrayList<>();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        calculateCaloriesButton.setOnClickListener(v -> calculateExerciseCalories());
        addExerciseButton.setOnClickListener(v -> addExerciseToList());
        saveExercisesButton.setOnClickListener(v -> saveExercisesLog());
    }

    private void initializeExerciseDatabase() {
        exerciseDatabase = new HashMap<>();

        // MET values (Metabolic Equivalent of Task)
        exerciseDatabase.put("Running", new ExerciseData("Running", 7.0, "moderate pace"));
        exerciseDatabase.put("Cycling", new ExerciseData("Cycling", 6.0, "moderate pace"));
        exerciseDatabase.put("Swimming", new ExerciseData("Swimming", 5.8, "moderate pace"));
        exerciseDatabase.put("Walking", new ExerciseData("Walking", 3.5, "brisk pace"));
        exerciseDatabase.put("Weight Lifting", new ExerciseData("Weight Lifting", 3.0, "general"));
        exerciseDatabase.put("Yoga", new ExerciseData("Yoga", 2.5, "hatha"));
        exerciseDatabase.put("Pilates", new ExerciseData("Pilates", 3.0, "general"));
        exerciseDatabase.put("Basketball", new ExerciseData("Basketball", 6.5, "general"));
        exerciseDatabase.put("Soccer", new ExerciseData("Soccer", 7.0, "general"));
        exerciseDatabase.put("Tennis", new ExerciseData("Tennis", 5.0, "general"));
        exerciseDatabase.put("HIIT", new ExerciseData("HIIT", 8.0, "high intensity"));
        exerciseDatabase.put("Dancing", new ExerciseData("Dancing", 4.5, "aerobic"));
        exerciseDatabase.put("Hiking", new ExerciseData("Hiking", 4.5, "with pack"));
        exerciseDatabase.put("Jump Rope", new ExerciseData("Jump Rope", 8.8, "moderate pace"));
        exerciseDatabase.put("Rowing", new ExerciseData("Rowing", 6.0, "moderate pace"));

        // Setup exercise spinner
        List<String> exercises = new ArrayList<>(exerciseDatabase.keySet());
        ArrayAdapter<String> exerciseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, exercises);
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseSpinner.setAdapter(exerciseAdapter);
    }

    private void updateDate() {
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String displayDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        dateText.setText(displayDate);
    }

    private void loadUserData() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            // Load user weight
                            if (documentSnapshot.contains("weight")) {
                                userWeight = documentSnapshot.getDouble("weight");
                            }

                            // Load preferred exercises
                            if (documentSnapshot.contains("preferredExercises")) {
                                userPreferredExercises = (List<String>) documentSnapshot.get("preferredExercises");
                                updateSuggestedExercises();
                            } else {
                                userPreferredExercises = new ArrayList<>();
                                suggestedExercisesText.setText("Add exercise preferences in your profile for personalized suggestions");
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("ExerciseActivity", "Error loading user data", e);
                    }
                });
    }

    private void updateSuggestedExercises() {
        suggestedExercisesLayout.removeAllViews();

        if (userPreferredExercises != null && !userPreferredExercises.isEmpty()) {
            suggestedExercisesText.setText("Based on your preferences: " + String.join(", ", userPreferredExercises));

            // Show buttons for preferred exercises
            for (String exercise : userPreferredExercises) {
                if (exerciseDatabase.containsKey(exercise)) {
                    Button exerciseButton = (Button) LayoutInflater.from(this)
                            .inflate(R.layout.suggested_exercise_button, suggestedExercisesLayout, false);
                    exerciseButton.setText(exercise);

                    exerciseButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Auto-select this exercise in spinner
                            String selectedExercise = ((Button) v).getText().toString();
                            int position = ((ArrayAdapter) exerciseSpinner.getAdapter()).getPosition(selectedExercise);
                            if (position >= 0) {
                                exerciseSpinner.setSelection(position);
                            }
                            durationInput.requestFocus();
                        }
                    });

                    suggestedExercisesLayout.addView(exerciseButton);
                }
            }
        } else {
            suggestedExercisesText.setText("Add exercise preferences in your profile for personalized suggestions");
        }
    }

    private void calculateExerciseCalories() {
        String durationStr = durationInput.getText().toString().trim();

        if (TextUtils.isEmpty(durationStr)) {
            durationInput.setError("Enter duration");
            return;
        }

        try {
            int duration = Integer.parseInt(durationStr);
            if (duration <= 0) {
                durationInput.setError("Duration must be positive");
                return;
            }

            String selectedExercise = exerciseSpinner.getSelectedItem().toString();
            ExerciseData exercise = exerciseDatabase.get(selectedExercise);

            if (exercise != null) {
                // Calculate calories burned: MET * weight * time(hours)
                double hours = duration / 60.0;
                double calories = exercise.metValue * userWeight * hours;

                currentExerciseName = selectedExercise;
                currentExerciseDuration = duration;
                currentExerciseCalories = (int) Math.round(calories);

                // Show result
                caloriesResultText.setText(currentExerciseCalories + " cal");
                caloriesResultLayout.setVisibility(View.VISIBLE);
                addExerciseButton.setVisibility(View.VISIBLE);

                // Show accuracy note
                Toast.makeText(this,
                        "Note: Calorie estimates may vary by ±30% based on intensity and individual factors",
                        Toast.LENGTH_LONG).show();

            }

        } catch (NumberFormatException e) {
            durationInput.setError("Invalid duration");
        }
    }

    private void addExerciseToList() {
        if (currentExerciseCalories > 0) {
            ExerciseItem exerciseItem = new ExerciseItem(
                    currentExerciseName,
                    currentExerciseDuration,
                    currentExerciseCalories,
                    false // not completed by default
            );

            todayExercises.add(exerciseItem);
            updateExercisesListDisplay();

            // Reset form
            durationInput.setText("");
            caloriesResultLayout.setVisibility(View.GONE);
            addExerciseButton.setVisibility(View.GONE);

            Toast.makeText(this, "Exercise added to list", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateExercisesListDisplay() {
        exercisesListLayout.removeAllViews();
        totalCaloriesBurned = 0;

        for (int i = 0; i < todayExercises.size(); i++) {
            ExerciseItem exercise = todayExercises.get(i);
            View exerciseView = LayoutInflater.from(this).inflate(R.layout.exercise_item_layout, exercisesListLayout, false);

            TextView nameText = exerciseView.findViewById(R.id.exerciseNameText);
            TextView durationText = exerciseView.findViewById(R.id.exerciseDurationText);
            TextView caloriesText = exerciseView.findViewById(R.id.exerciseCaloriesText);
            CheckBox completedCheckbox = exerciseView.findViewById(R.id.exerciseCompletedCheckbox);

            nameText.setText(exercise.name);
            durationText.setText(exercise.duration + " mins");
            caloriesText.setText(exercise.calories + " cal");
            completedCheckbox.setChecked(exercise.completed);

            // Only count calories for completed exercises
            if (exercise.completed) {
                totalCaloriesBurned += exercise.calories;
            }

            final int position = i;
            completedCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    todayExercises.get(position).completed = isChecked;
                    updateTotalCaloriesBurned();
                }
            });

            exercisesListLayout.addView(exerciseView);
        }

        updateTotalCaloriesBurned();
    }

    private void updateTotalCaloriesBurned() {
        totalCaloriesBurned = 0;
        for (ExerciseItem exercise : todayExercises) {
            if (exercise.completed) {
                totalCaloriesBurned += exercise.calories;
            }
        }
        totalCaloriesBurnedText.setText(String.valueOf(totalCaloriesBurned));
    }

    private void loadTodayExercises() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        firestore.collection("users").document(userId)
                .collection("exerciseLogs").document(currentDate)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            // Load existing exercises
                            if (documentSnapshot.contains("totalCaloriesBurned")) {
                                totalCaloriesBurned = documentSnapshot.getLong("totalCaloriesBurned").intValue();
                            }

                            if (documentSnapshot.contains("exercises")) {
                                List<Map<String, Object>> exercisesData =
                                        (List<Map<String, Object>>) documentSnapshot.get("exercises");

                                if (exercisesData != null) {
                                    todayExercises.clear();
                                    for (Map<String, Object> exerciseData : exercisesData) {
                                        String name = (String) exerciseData.get("name");
                                        long duration = (Long) exerciseData.get("duration");
                                        long calories = (Long) exerciseData.get("calories");
                                        boolean completed = exerciseData.get("completed") != null ?
                                                (Boolean) exerciseData.get("completed") : false;

                                        todayExercises.add(new ExerciseItem(name, (int) duration, (int) calories, completed));
                                    }

                                    // Update UI
                                    updateExercisesListDisplay();
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("ExerciseActivity", "Error loading exercises", e);
                    }
                });
    }

    private void saveExercisesLog() {
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();

        // Prepare exercises data for Firestore
        List<Map<String, Object>> exercisesData = new ArrayList<>();
        for (ExerciseItem exercise : todayExercises) {
            Map<String, Object> exerciseData = new HashMap<>();
            exerciseData.put("name", exercise.name);
            exerciseData.put("duration", exercise.duration);
            exerciseData.put("calories", exercise.calories);
            exerciseData.put("completed", exercise.completed);
            exercisesData.add(exerciseData);
        }

        // Prepare the exercise log document
        Map<String, Object> exerciseLog = new HashMap<>();
        exerciseLog.put("date", currentDate);
        exerciseLog.put("totalCaloriesBurned", totalCaloriesBurned);
        exerciseLog.put("exercises", exercisesData);
        exerciseLog.put("timestamp", new Date());

        // Save to Firestore
        firestore.collection("users").document(userId)
                .collection("exerciseLogs").document(currentDate)
                .set(exerciseLog)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Update main activity calories burned
                        updateMainCaloriesBurned();
                        Toast.makeText(ExerciseActivity.this, "Exercise log saved successfully!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("ExerciseActivity", "Error saving exercise log", e);
                        Toast.makeText(ExerciseActivity.this, "Error saving exercise log", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateMainCaloriesBurned() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();

        // Update the main user document with today's calories burned
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("caloriesBurned", totalCaloriesBurned);
        updateData.put("lastUpdated", new Date());

        firestore.collection("users").document(userId)
                .collection("dailyData").document(currentDate)
                .set(updateData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("ExerciseActivity", "Main calories burned updated successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("ExerciseActivity", "Error updating main calories burned", e);
                    }
                });
    }

    // Exercise data class
    private static class ExerciseData {
        String name;
        double metValue;
        String intensity;

        ExerciseData(String name, double metValue, String intensity) {
            this.name = name;
            this.metValue = metValue;
            this.intensity = intensity;
        }
    }

    // Exercise item class
    private static class ExerciseItem {
        String name;
        int duration;
        int calories;
        boolean completed;

        ExerciseItem(String name, int duration, int calories, boolean completed) {
            this.name = name;
            this.duration = duration;
            this.calories = calories;
            this.completed = completed;
        }
    }
}