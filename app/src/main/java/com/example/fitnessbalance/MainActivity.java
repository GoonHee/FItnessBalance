package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView welcomeText, calorieBalanceText, dateText;
    private TextView consumedCaloriesText, burnedCaloriesText, netCaloriesText;
    private CardView foodLogCard, exerciseCard, profileCard;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        FirebaseAuth auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();

        // Initial data load
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
            // Using startActivityForResult can be more robust for immediate UI updates,
            // but onResume is also a common pattern.
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
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Get user data
                            userName = documentSnapshot.getString("fullName");
                            updateWelcomeMessage();

                            // Chain the next data load here to ensure sequence
                            loadTodayMetrics();
                        } else {
                            Log.w("MainActivity", "User document doesn't exist");
                            // If user doc doesn't exist, show default values
                            updateCalorieDisplay(0, 0);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MainActivity", "Error loading user data", e);
                        Toast.makeText(MainActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Handle signed-out state
            welcomeText.setText("Welcome!");
            updateCalorieDisplay(0, 0);
        }
    }

    private void updateWelcomeMessage() {
        if (userName != null && !userName.isEmpty()) {
            welcomeText.setText("Hello, " + userName + "!");
        } else {
            welcomeText.setText("Welcome to FitnessBalance!");
        }
    }

    private void loadTodayMetrics() {
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        firestore.collection("users").document(userId)
                .collection("dailyData").document(todayDate)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    int consumed = 0;
                    int burned = 0;
                    if (documentSnapshot.exists()) {
                        // Use .get() which returns an Object, then safely cast
                        Object consumedObj = documentSnapshot.get("caloriesConsumed");
                        if (consumedObj instanceof Number) {
                            consumed = ((Number) consumedObj).intValue();
                        }

                        Object burnedObj = documentSnapshot.get("caloriesBurned");
                        if (burnedObj instanceof Number) {
                            burned = ((Number) burnedObj).intValue();
                        }
                    }
                    // FIX: Pass the newly fetched data directly to the update method
                    updateCalorieDisplay(consumed, burned);
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "Error loading today's metrics", e);
                    // On failure, update the UI with 0s to avoid showing stale data
                    updateCalorieDisplay(0, 0);
                });
    }

    // FIX: Modified this method to accept calorie values as parameters
    private void updateCalorieDisplay(int caloriesConsumed, int caloriesBurned) {
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
            balanceColor = getColor(R.color.calorie_surplus);
        } else if (netCalories < 0) {
            balanceMessage = "You have a deficit of " + Math.abs(netCalories) + " calories";
            balanceColor = getColor(R.color.calorie_deficit);
        } else {
            balanceMessage = "Perfect balance!";
            balanceColor = getColor(R.color.calorie_balanced);
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
        // Refresh all data when returning to this activity
        loadUserData();
    }
}