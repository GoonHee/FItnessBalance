package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextView welcomeText, calorieBalanceText, dateText;
    private TextView consumedCaloriesText, burnedCaloriesText, netCaloriesText;
    // FIX 1: Removed historyCard from this line
    private CardView foodLogCard, exerciseCard, profileCard;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // User data
    private String userName;
    private double userWeight;
    private String fitnessLevel;

    // Today's metrics (these would come from database)
    private int caloriesConsumed = 0;
    private int caloriesBurned = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();
        loadUserData();
        updateDate();
    }

    private void initializeViews() {
        // TextViews
        welcomeText = findViewById(R.id.welcomeText);
        calorieBalanceText = findViewById(R.id.calorieBalanceText);
        dateText = findViewById(R.id.dateText);
        consumedCaloriesText = findViewById(R.id.consumedCaloriesText);
        burnedCaloriesText = findViewById(R.id.burnedCaloriesText);
        netCaloriesText = findViewById(R.id.netCaloriesText);

        // CardViews for navigation
        foodLogCard = findViewById(R.id.foodLogCard);
        exerciseCard = findViewById(R.id.exerciseCard);
        profileCard = findViewById(R.id.profileCard);
    }

    private void setupClickListeners() {
        // Navigation Cards
        foodLogCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FoodLogActivity.class));
        });

        exerciseCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ExerciseActivity.class));
        });

        profileCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });
    }

    private void loadUserData() {
        if (currentUser != null) {
            String userId = currentUser.getUid();

            firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (documentSnapshot.exists()) {
                                // Get user data
                                userName = documentSnapshot.getString("fullName");
                                // Make sure to handle potential null values for weight
                                if (documentSnapshot.contains("weight")) {
                                    userWeight = documentSnapshot.getDouble("weight");
                                }
                                fitnessLevel = documentSnapshot.getString("fitnessLevel");

                                // Update UI with user data
                                updateWelcomeMessage();
                                loadTodayMetrics(); // Load today's calorie data
                            } else {
                                Log.w("MainActivity", "User document doesn't exist");
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("MainActivity", "Error loading user data", e);
                            Toast.makeText(MainActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void updateWelcomeMessage() {
        if (userName != null && !userName.isEmpty()) {
            String welcomeMessage = "Hello, " + userName + "!";
            welcomeText.setText(welcomeMessage);
        } else {
            welcomeText.setText("Welcome to FitnessBalance+!");
        }
    }

    private void loadTodayMetrics() {
        if (currentUser != null) {
            String userId = currentUser.getUid();
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // Load today's data from Firestore
            firestore.collection("users").document(userId)
                    .collection("dailyData").document(todayDate)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (documentSnapshot.exists()) {
                                if (documentSnapshot.contains("caloriesConsumed")) {
                                    caloriesConsumed = documentSnapshot.getLong("caloriesConsumed").intValue();
                                } else {
                                    caloriesConsumed = 0;
                                }

                                if (documentSnapshot.contains("caloriesBurned")) {
                                    caloriesBurned = documentSnapshot.getLong("caloriesBurned").intValue();
                                } else {
                                    caloriesBurned = 0;
                                }
                            } else {
                                // Default values if no data
                                caloriesConsumed = 0;
                                caloriesBurned = 0;
                            }

                            updateCalorieDisplay();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("MainActivity", "Error loading today's metrics", e);
                            caloriesConsumed = 0;
                            caloriesBurned = 0;
                            updateCalorieDisplay();
                        }
                    });
        }
    }

    private void updateCalorieDisplay() {
        int netCalories = caloriesConsumed - caloriesBurned;

        // Update individual metrics
        consumedCaloriesText.setText(String.valueOf(caloriesConsumed));
        burnedCaloriesText.setText(String.valueOf(caloriesBurned));
        netCaloriesText.setText(String.valueOf(netCalories));

        // Update balance message
        String balanceMessage;
        int balanceColor;

        if (netCalories > 0) {
            balanceMessage = "You have a surplus of " + netCalories + " calories";
            balanceColor = getColor(R.color.calorie_surplus); // Assumes you have this color
        } else if (netCalories < 0) {
            balanceMessage = "You have a deficit of " + Math.abs(netCalories) + " calories";
            balanceColor = getColor(R.color.calorie_deficit); // Assumes you have this color
        } else {
            balanceMessage = "Perfect balance!";
            balanceColor = getColor(R.color.calorie_balanced); // Assumes you have this color
        }

        calorieBalanceText.setText(balanceMessage);
        calorieBalanceText.setTextColor(balanceColor);
    }

    private void updateDate() {
        String currentDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        dateText.setText(currentDate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to this activity
        if (currentUser != null) {
            // It's better to reload all user data in case it changed
            loadUserData();
        }
    }
}
