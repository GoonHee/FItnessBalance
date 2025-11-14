package com.example.fitnessbalance;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.content.Context;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.widget.CheckBox;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import com.google.android.flexbox.FlexboxLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // UI Components
    private TextView profileName, profileEmail;
    private TextInputEditText fullNameEditText, weightEditText, heightEditText;
    private Spinner fitnessLevelSpinner;
    private Button editProfileButton, saveProfileButton, cancelButton, logoutButton, addExerciseButton;
    private LinearLayout bmiResultsLayout;
    private TextView bmiValueText, bmiCategoryText;
    private ImageButton backButton;
    private FlexboxLayout exerciseTagsLayout;

    // Exercise selection dialog components
    private CheckBox runningCheckbox, cyclingCheckbox, swimmingCheckbox, weightliftingCheckbox;
    private CheckBox yogaCheckbox, pilatesCheckbox, basketballCheckbox, footballCheckbox;
    private CheckBox tennisCheckbox, hiitCheckbox, dancingCheckbox, hikingCheckbox;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // User data
    private String userName, userEmail;
    private double userWeight;
    private double userHeight;
    private String fitnessLevel;
    private List<String> preferredExercises;

    // Mode tracking
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // This code will execute when the user presses the back button
                if (isEditMode) {
                    // If in edit mode, show the confirmation dialog
                    new android.app.AlertDialog.Builder(ProfileActivity.this)
                            .setTitle("Discard Changes?")
                            .setMessage("Are you sure you want to go back? All unsaved changes will be lost.")
                            .setPositiveButton("Discard", (dialog, which) -> {
                                // If the user chooses to discard, revert the UI and then finish.
                                // This prevents a flash of unsaved content.
                                updateProfileDisplay();
                                setEditMode(false);
                                finish(); // Now, safely finish the activity.
                            })
                            .setNegativeButton("Cancel", null) // 'null' correctly dismisses the dialog
                            .show();
                } else {
                    // If not in edit mode, just finish the activity as normal
                    finish();
                }
            }
        });

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupSpinner();
        loadUserData();
        setupClickListeners();
        setEditMode(false); // Start in view mode
    }

    private void initializeViews() {
        // TextViews
        profileName = findViewById(R.id.profileName);
        profileEmail = findViewById(R.id.profileEmail);

        // EditTexts
        fullNameEditText = findViewById(R.id.fullNameEditText);
        weightEditText = findViewById(R.id.weightEditText);
        heightEditText = findViewById(R.id.heightEditText);

        backButton = findViewById(R.id.backButton);

        // Spinner
        fitnessLevelSpinner = findViewById(R.id.fitnessLevelSpinner);

        // BMI Components
        bmiResultsLayout = findViewById(R.id.bmiResultsLayout);
        bmiValueText = findViewById(R.id.bmiValueText);
        bmiCategoryText = findViewById(R.id.bmiCategoryText);

        // Exercise Preferences
        exerciseTagsLayout = findViewById(R.id.exerciseTagsLayout);
        addExerciseButton = findViewById(R.id.addExerciseButton);

        // Buttons
        editProfileButton = findViewById(R.id.editProfileButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        cancelButton = findViewById(R.id.cancelButton);
        logoutButton = findViewById(R.id.logoutButton);
    }

    private void setupSpinner() {
        // Create ArrayAdapter for fitness levels
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.fitness_levels,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fitnessLevelSpinner.setAdapter(adapter);
    }

    private void loadUserData() {
        if (currentUser != null) {
            userEmail = currentUser.getEmail();
            profileEmail.setText(userEmail);

            String userId = currentUser.getUid();

            firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (documentSnapshot.exists()) {
                                // Get user data
                                userName = documentSnapshot.getString("fullName");
                                if (documentSnapshot.contains("weight")) {
                                    userWeight = documentSnapshot.getDouble("weight");
                                }
                                if (documentSnapshot.contains("height")) {
                                    userHeight = documentSnapshot.getDouble("height");
                                }
                                fitnessLevel = documentSnapshot.getString("fitnessLevel");

                                // Get exercise preferences
                                if (documentSnapshot.contains("preferredExercises")) {
                                    preferredExercises = (List<String>) documentSnapshot.get("preferredExercises");
                                } else {
                                    preferredExercises = new ArrayList<>();
                                }

                                // Update UI with user data
                                updateProfileDisplay();
                            } else {
                                Log.w("ProfileActivity", "User document doesn't exist");
                                // If no profile exists, start in edit mode
                                setEditMode(true);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("ProfileActivity", "Error loading user data", e);
                            Toast.makeText(ProfileActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void updateProfileDisplay() {
        // Set user name
        if (userName != null && !userName.isEmpty()) {
            profileName.setText(userName);
            fullNameEditText.setText(userName);
        } else {
            profileName.setText("Welcome!");
        }

        // Set weight
        if (userWeight > 0) {
            weightEditText.setText(String.valueOf(userWeight));
        } else {
            weightEditText.setText("");
        }

        // Set height
        if (userHeight > 0) {
            heightEditText.setText(String.valueOf(userHeight));
        } else {
            heightEditText.setText("");
        }

        // Set fitness level in spinner
        if (fitnessLevel != null && !fitnessLevel.isEmpty()) {
            ArrayAdapter adapter = (ArrayAdapter) fitnessLevelSpinner.getAdapter();
            int position = adapter.getPosition(fitnessLevel);
            if (position >= 0) {
                fitnessLevelSpinner.setSelection(position);
            }
        }

        // Set exercise preferences
        updateExercisePreferencesDisplay();

        // Auto-calculate BMI if both weight and height are available
        if (userWeight > 0 && userHeight > 0) {
            calculateBMI();
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        editProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEditMode(true);
            }
        });

        saveProfileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveProfileData();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateProfileDisplay();
                setEditMode(false);
            }
        });

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });

        addExerciseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExerciseSelectionDialog();
            }
        });
    }

    private void setEditMode(boolean editMode) {
        isEditMode = editMode;

        if (editMode) {
            // Edit mode - make fields editable and show save/cancel buttons
            fullNameEditText.setEnabled(true);
            weightEditText.setEnabled(true);
            heightEditText.setEnabled(true);
            fitnessLevelSpinner.setEnabled(true);
            addExerciseButton.setVisibility(View.VISIBLE);

            editProfileButton.setVisibility(View.GONE);
            saveProfileButton.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.VISIBLE);

            // Make sure exercise tags are updated for edit mode
            updateExerciseTags();
        } else {
            // View mode - make fields static and show edit button
            fullNameEditText.setEnabled(false);
            weightEditText.setEnabled(false);
            heightEditText.setEnabled(false);
            fitnessLevelSpinner.setEnabled(false);
            addExerciseButton.setVisibility(View.GONE);

            editProfileButton.setVisibility(View.VISIBLE);
            saveProfileButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);

            // Make sure exercise tags are updated for view mode
            updateExerciseTags();
        }
    }

    private void calculateBMI() {
        String weightStr = weightEditText.getText().toString().trim();
        String heightStr = heightEditText.getText().toString().trim();

        if (weightStr.isEmpty() || heightStr.isEmpty()) {
            bmiResultsLayout.setVisibility(View.GONE);
            return;
        }

        try {
            double weight = Double.parseDouble(weightStr);
            double height = Double.parseDouble(heightStr);

            if (weight <= 0 || height <= 0) {
                bmiResultsLayout.setVisibility(View.GONE);
                return;
            }

            // Calculate BMI: weight (kg) / (height (m) * height (m))
            // Height is in cm, so convert to meters by dividing by 100
            double heightInMeters = height / 100.0;
            double bmi = weight / (heightInMeters * heightInMeters);

            displayBMIResult(bmi);

        } catch (NumberFormatException e) {
            bmiResultsLayout.setVisibility(View.GONE);
        }
    }

    private void displayBMIResult(double bmi) {
        // Format BMI to 1 decimal place
        String bmiFormatted = String.format(Locale.getDefault(), "%.1f", bmi);
        bmiValueText.setText(bmiFormatted);

        // Determine BMI category
        String category;
        int color;

        if (bmi < 18.5) {
            category = "Underweight";
            color = getResources().getColor(R.color.bmi_underweight);
        } else if (bmi < 25) {
            category = "Normal";
            color = getResources().getColor(R.color.bmi_normal);
        } else if (bmi < 30) {
            category = "Overweight";
            color = getResources().getColor(R.color.bmi_overweight);
        } else {
            category = "Obese";
            color = getResources().getColor(R.color.bmi_obese);
        }

        bmiCategoryText.setText(category);
        bmiCategoryText.setTextColor(color);

        // Show results layout
        bmiResultsLayout.setVisibility(View.VISIBLE);
    }

    private void showExerciseSelectionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_exercise_input);
        dialog.setCancelable(true);

        // Get dialog views
        FlexboxLayout quickAddLayout = dialog.findViewById(R.id.quickAddLayout);
        FlexboxLayout selectedExercisesLayout = dialog.findViewById(R.id.selectedExercisesLayout);
        TextInputEditText customExerciseInput = dialog.findViewById(R.id.customExerciseInput);
        Button addCustomExerciseButton = dialog.findViewById(R.id.addCustomExerciseButton);
        Button cancelButton = dialog.findViewById(R.id.cancelExerciseButton);
        Button saveButton = dialog.findViewById(R.id.saveExerciseButton);

        // Temporary list for selected exercises in this dialog
        List<String> tempSelectedExercises = new ArrayList<>();
        if (preferredExercises != null) {
            tempSelectedExercises.addAll(preferredExercises);
        }

        // Popular exercises for quick add
        String[] popularExercises = {
                "Running", "Cycling", "Swimming", "Weight Lifting", "Yoga",
                "Pilates", "Basketball", "Football", "Tennis", "HIIT",
                "Dancing", "Hiking", "Walking", "Jump Rope", "Rowing",
                "Boxing", "Martial Arts", "Skating", "Skiing", "Climbing"
        };

        // Add quick add buttons
        for (String exercise : popularExercises) {
            Button quickButton = (Button) LayoutInflater.from(this).inflate(R.layout.quick_exercise_button, quickAddLayout, false);
            quickButton.setText(exercise);

            quickButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String selectedExercise = ((Button) v).getText().toString();
                    if (!tempSelectedExercises.contains(selectedExercise)) {
                        tempSelectedExercises.add(selectedExercise);
                        updateSelectedExercisesInDialog(selectedExercisesLayout, tempSelectedExercises);
                    } else {
                        Toast.makeText(ProfileActivity.this, "Exercise already added", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            quickAddLayout.addView(quickButton);
        }

        // Update selected exercises display
        updateSelectedExercisesInDialog(selectedExercisesLayout, tempSelectedExercises);

        // Custom exercise input
        addCustomExerciseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String customExercise = customExerciseInput.getText().toString().trim();
                if (!customExercise.isEmpty()) {
                    if (!tempSelectedExercises.contains(customExercise)) {
                        tempSelectedExercises.add(customExercise);
                        updateSelectedExercisesInDialog(selectedExercisesLayout, tempSelectedExercises);
                        customExerciseInput.setText("");
                    } else {
                        Toast.makeText(ProfileActivity.this, "Exercise already added", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    customExerciseInput.setError("Enter an exercise name");
                }
            }
        });

        // Handle Enter key in custom input
        customExerciseInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                addCustomExerciseButton.performClick();
                return true;
            }
            return false;
        });

        // Button actions
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preferredExercises = new ArrayList<>(tempSelectedExercises);
                updateExerciseTags();
                dialog.dismiss();
            }
        });

        dialog.show();

        // Show keyboard for custom input
        customExerciseInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(customExerciseInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void updateSelectedExercisesInDialog(FlexboxLayout layout, List<String> exercises) {
        layout.removeAllViews();

        if (exercises.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No exercises selected");
            emptyText.setTextSize(14);
            emptyText.setTextColor(Color.parseColor("#666666"));
            emptyText.setGravity(Gravity.CENTER);
            layout.addView(emptyText);
        } else {
            for (String exercise : new ArrayList<>(exercises)) {
                View tagView = LayoutInflater.from(this).inflate(R.layout.exercise_tag_item, layout, false);
                TextView tagText = tagView.findViewById(R.id.tagText);
                ImageButton removeButton = tagView.findViewById(R.id.removeTagButton);

                tagText.setText(exercise);

                removeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exercises.remove(exercise);
                        updateSelectedExercisesInDialog(layout, exercises);
                    }
                });

                layout.addView(tagView);
            }
        }
    }

    private void updateExerciseTags() {
        exerciseTagsLayout.removeAllViews();

        if (preferredExercises != null && !preferredExercises.isEmpty()) {
            for (String exercise : new ArrayList<>(preferredExercises)) {                View tagView = LayoutInflater.from(this).inflate(R.layout.exercise_tag_item, exerciseTagsLayout, false);
                TextView tagText = tagView.findViewById(R.id.tagText);
                ImageButton removeButton = tagView.findViewById(R.id.removeTagButton);

                tagText.setText(exercise);

                if (isEditMode) {
                    removeButton.setVisibility(View.VISIBLE); // Make the button visible
                    removeButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // This logic is now only active during edit mode
                            preferredExercises.remove(exercise);
                            updateExerciseTags(); // Redraw the tags for this screen
                        }
                    });
                } else {
                    // Ensure the button is hidden if not in edit mode
                    removeButton.setVisibility(View.GONE);
                }

                exerciseTagsLayout.addView(tagView);
            }
        } else {
            // Show empty state message
            TextView emptyText = new TextView(this);
            emptyText.setText("No exercises selected yet");
            emptyText.setTextSize(12);
            emptyText.setTextColor(Color.parseColor("#666666"));
            emptyText.setGravity(Gravity.CENTER);
            exerciseTagsLayout.addView(emptyText);
        }
    }

    private void updateExercisePreferencesDisplay() {
        updateExerciseTags();
    }

    private void saveProfileData() {
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get input values
        String fullName = fullNameEditText.getText().toString().trim();
        String weightStr = weightEditText.getText().toString().trim();
        String heightStr = heightEditText.getText().toString().trim();
        String selectedFitnessLevel = fitnessLevelSpinner.getSelectedItem().toString();

        // Validate inputs
        if (fullName.isEmpty()) {
            fullNameEditText.setError("Full name is required");
            return;
        }

        double weight = 0;
        if (!weightStr.isEmpty()) {
            try {
                weight = Double.parseDouble(weightStr);
                if (weight <= 0) {
                    weightEditText.setError("Weight must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                weightEditText.setError("Invalid weight format");
                return;
            }
        }

        double height = 0;
        if (!heightStr.isEmpty()) {
            try {
                height = Double.parseDouble(heightStr);
                if (height <= 0) {
                    heightEditText.setError("Height must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                heightEditText.setError("Invalid height format");
                return;
            }
        }

        final double finalWeight = weight;
        final double finalHeight = height;

        // Save to Firestore
        String userId = currentUser.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("weight", weight);
        userData.put("height", height);
        userData.put("fitnessLevel", selectedFitnessLevel);
        userData.put("preferredExercises", preferredExercises);

        firestore.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(ProfileActivity.this, "Profile saved successfully!", Toast.LENGTH_SHORT).show();

                        // Update local variables
                        userName = fullName;
                        userWeight = finalWeight;
                        userHeight = finalHeight;
                        fitnessLevel = selectedFitnessLevel;
                        ProfileActivity.this.preferredExercises = preferredExercises;

                        // Update the display name
                        profileName.setText(userName);

                        // Calculate and show BMI if both weight and height are provided
                        if (finalWeight > 0 && finalHeight > 0) {
                            calculateBMI();
                        }

                        // Switch back to view mode
                        setEditMode(false);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("ProfileActivity", "Error saving profile", e);
                        Toast.makeText(ProfileActivity.this, "Error saving profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void logoutUser() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    auth.signOut();
                    startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
                    finishAffinity(); // Use finishAffinity to clear all previous activities
                })
                .setNegativeButton("No", null)
                .show();
    }
}