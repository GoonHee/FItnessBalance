package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FoodLogActivity extends AppCompatActivity {
    private TextInputEditText foodNameInput, caloriesInput;
    private Button addFoodButton, saveLogButton, calculateCaloriesButton;
    private ImageButton backButton;
    private LinearLayout foodItemsLayout;
    private TextView dateText, totalCaloriesText;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private List<FoodItem> foodItems;
    private int totalCalories = 0;

    private String currentDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_log);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();
        updateDate();
        loadTodayFoodLog();
    }

    private void initializeViews() {
        foodNameInput = findViewById(R.id.foodNameInput);
        caloriesInput = findViewById(R.id.caloriesInput);

        addFoodButton = findViewById(R.id.addFoodButton);
        saveLogButton = findViewById(R.id.saveLogButton);
        backButton = findViewById(R.id.backButton);
        calculateCaloriesButton = findViewById(R.id.calculateCaloriesButton);

        foodItemsLayout = findViewById(R.id.foodItemsLayout);
        dateText = findViewById(R.id.dateText);
        totalCaloriesText = findViewById(R.id.totalCaloriesText);

        foodItems = new ArrayList<>();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        addFoodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFoodItem();
            }
        });

        saveLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFoodLog();
            }
        });

        calculateCaloriesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FoodLogActivity.this, CalorieCalculatorActivity.class);
                startActivityForResult(intent, 1);
            }
        });

        findViewById(R.id.searchFoodButton).setOnClickListener(v ->
                startActivity(new Intent(
                        FoodLogActivity.this,
                        FoodSearchActivity.class
                ))
        );
    }

    private void updateDate() {
        currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String displayDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        dateText.setText(displayDate);
    }

    private void addFoodItem() {
        String foodName = foodNameInput.getText().toString().trim();
        String caloriesStr = caloriesInput.getText().toString().trim();

        if (TextUtils.isEmpty(foodName)) {
            foodNameInput.setError("Food name is required");
            return;
        }

        if (TextUtils.isEmpty(caloriesStr)) {
            caloriesInput.setError("Calories are required");
            return;
        }

        try {
            int calories = Integer.parseInt(caloriesStr);
            if (calories <= 0) {
                caloriesInput.setError("Calories must be positive");
                return;
            }
            FoodItem foodItem = new FoodItem(foodName, calories);
            foodItems.add(foodItem);
            totalCalories += calories;
            updateTotalCaloriesDisplay();
            addFoodItemToLayout(foodItem);
            foodNameInput.setText("");
            caloriesInput.setText("");
            foodNameInput.requestFocus();

        } catch (NumberFormatException e) {
            caloriesInput.setError("Invalid calories format");
        }
    }

    private void addFoodItemToLayout(FoodItem foodItem) {
        View foodItemView = LayoutInflater.from(this).inflate(R.layout.food_item_layout, foodItemsLayout, false);

        TextView foodNameText = foodItemView.findViewById(R.id.foodNameText);
        TextView foodCaloriesText = foodItemView.findViewById(R.id.foodCaloriesText);
        ImageButton removeButton = foodItemView.findViewById(R.id.removeFoodButton);

        foodNameText.setText(foodItem.getName());
        foodCaloriesText.setText(foodItem.getCalories() + " cal");

        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeFoodItem(foodItem, foodItemView);
            }
        });

        foodItemsLayout.addView(foodItemView);
    }

    private void removeFoodItem(FoodItem foodItem, View foodItemView) {
        foodItems.remove(foodItem);
        totalCalories -= foodItem.getCalories();
        updateTotalCaloriesDisplay();
        foodItemsLayout.removeView(foodItemView);
    }

    private void updateTotalCaloriesDisplay() {
        totalCaloriesText.setText(String.valueOf(totalCalories));
    }

    private void loadTodayFoodLog() {
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        firestore.collection("users").document(userId)
                .collection("foodLogs").document(currentDate)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            if (documentSnapshot.contains("totalCalories")) {
                                totalCalories = documentSnapshot.getLong("totalCalories").intValue();
                            }

                            if (documentSnapshot.contains("foodItems")) {
                                List<Map<String, Object>> foodItemsData =
                                        (List<Map<String, Object>>) documentSnapshot.get("foodItems");

                                if (foodItemsData != null) {
                                    foodItems.clear();
                                    for (Map<String, Object> foodData : foodItemsData) {
                                        String name = (String) foodData.get("name");
                                        long calories = (Long) foodData.get("calories");
                                        foodItems.add(new FoodItem(name, (int) calories));
                                    }
                                    updateFoodItemsDisplay();
                                    updateTotalCaloriesDisplay();
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("FoodLogActivity", "Error loading food log", e);
                    }
                });
    }

    private void updateFoodItemsDisplay() {
        foodItemsLayout.removeAllViews();
        for (FoodItem foodItem : foodItems) {
            addFoodItemToLayout(foodItem);
        }
    }

    private void saveFoodLog() {
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        if (foodItems.isEmpty()) {
            Toast.makeText(this, "No food items to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        List<Map<String, Object>> foodItemsData = new ArrayList<>();
        for (FoodItem foodItem : foodItems) {
            Map<String, Object> foodData = new HashMap<>();
            foodData.put("name", foodItem.getName());
            foodData.put("calories", foodItem.getCalories());
            foodItemsData.add(foodData);
        }

        Map<String, Object> foodLog = new HashMap<>();
        foodLog.put("date", currentDate);
        foodLog.put("totalCalories", totalCalories);
        foodLog.put("foodItems", foodItemsData);
        foodLog.put("timestamp", new Date());

        firestore.collection("users").document(userId)
                .collection("foodLogs").document(currentDate)
                .set(foodLog)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(FoodLogActivity.this, "Food log saved successfully!", Toast.LENGTH_SHORT).show();
                        updateMainCaloriesConsumed();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("FoodLogActivity", "Error saving food log", e);
                        Toast.makeText(FoodLogActivity.this, "Error saving food log", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String foodName = data.getStringExtra("foodName");
            int calories = data.getIntExtra("calories", 0);
            foodNameInput.setText(foodName);
            caloriesInput.setText(String.valueOf(calories));
            Toast.makeText(this, "Calories calculated: " + calories, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMainCaloriesConsumed() {
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("caloriesConsumed", totalCalories);
        updateData.put("lastUpdated", new Date());

        firestore.collection("users").document(userId)
                .collection("dailyData").document(currentDate)
                .set(updateData, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("FoodLogActivity", "Main calories updated successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("FoodLogActivity", "Error updating main calories", e);
                    }
                });
    }

    private static class FoodItem {
        private String name;
        private int calories;

        public FoodItem(String name, int calories) {
            this.name = name;
            this.calories = calories;
        }
        public String getName() {
            return name;
        }
        public int getCalories() {
            return calories;
        }
    }
}