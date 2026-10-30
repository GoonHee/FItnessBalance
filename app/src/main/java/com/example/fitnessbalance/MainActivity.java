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
    private com.example.fitnessbalance.util.LocalDayObserver days;
    private int metricsRequest;
    private String displayDay;

    private void refreshToday() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String today = com.example.fitnessbalance.util.LocalDayObserver.today();
        if (!today.equals(displayDay)) {
            displayDay = today;
            updateCalorieDisplay(0, 0);
        }
        updateDate();
        loadTodayMetrics();
    }

    private boolean currentMetrics(int token, String day, String userId) {
        FirebaseUser active = FirebaseAuth.getInstance().getCurrentUser();
        return token == metricsRequest && active != null && userId.equals(active.getUid())
            && day.equals(com.example.fitnessbalance.util.LocalDayObserver.today());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();

        days = new com.example.fitnessbalance.util.LocalDayObserver(this, this, null, this::refreshToday);
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeText);
        calorieBalanceText = findViewById(R.id.calorieBalanceText);
        dateText = findViewById(R.id.dateText);
        consumedCaloriesText = findViewById(R.id.consumedCaloriesText);
        burnedCaloriesText = findViewById(R.id.burnedCaloriesText);
        netCaloriesText = findViewById(R.id.netCaloriesText);
        foodLogCard = findViewById(R.id.foodLogCard);
        exerciseCard = findViewById(R.id.exerciseCard);
        profileCard = findViewById(R.id.profileCard);
    }

    private void setupClickListeners() {
        findViewById(R.id.progressCard).setOnClickListener(v -> startActivity(new Intent(this, ProgressActivity.class)));
        foodLogCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FoodLogActivity.class));
        });

        exerciseCard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, com.example.fitnessbalance.ui.exercise.ExerciseHomeActivity.class));
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
                            userName = documentSnapshot.getString("fullName");
                            updateWelcomeMessage();
                        } else {
                            Log.w("MainActivity", "User document doesn't exist");
                            userName = null;
                            updateWelcomeMessage();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MainActivity", "Error loading user data", e);
                        Toast.makeText(MainActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                    });
        } else {
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
        final int token = ++metricsRequest;
        if (currentUser == null) { updateCalorieDisplay(0, 0); return; }

        String userId = currentUser.getUid();
        String todayDate = com.example.fitnessbalance.util.LocalDayObserver.today();

        firestore.collection("users").document(userId)
                .collection("dailyData").document(todayDate)
                .get()
                .addOnSuccessListener(this, documentSnapshot -> {
                    if (!currentMetrics(token, todayDate, userId)) return;
                    int consumed = 0;
                    int burned = 0;
                    if (documentSnapshot.exists()) {
                        Object consumedObj = documentSnapshot.get("caloriesConsumed");
                        if (consumedObj instanceof Number) {
                            consumed = ((Number) consumedObj).intValue();
                        }
                        Object burnedObj = documentSnapshot.get("caloriesBurned");
                        if (burnedObj instanceof Number) {
                            burned = ((Number) burnedObj).intValue();
                        }
                    }
                    updateCalorieDisplay(consumed, burned);
                    refreshExerciseTotals(todayDate, userId, token);
                })
                .addOnFailureListener(e -> {
                    if (!currentMetrics(token, todayDate, userId)) return;
                    Log.e("MainActivity", "Error loading today's metrics", e);
                    updateCalorieDisplay(0, 0);
                });
    }


    private void refreshExerciseTotals(String day, String userId, int token) {
        new com.example.fitnessbalance.repository.ExerciseLogRepository(userId)
                .loadDay(day)
                .addOnSuccessListener(this, result -> {
                    if (!currentMetrics(token, day, userId)) return;
                    updateCalorieDisplay(result.caloriesConsumed,
                            com.example.fitnessbalance.util.Values.integer(result.totals.get("caloriesBurned"), 0));
                })
                .addOnFailureListener(this, error -> Log.w("MainActivity", "Exercise totals refresh: " + error.getClass().getSimpleName()));
    }

    private void updateCalorieDisplay(int caloriesConsumed, int caloriesBurned) {
        int netCalories = caloriesConsumed - caloriesBurned;

        consumedCaloriesText.setText(String.valueOf(caloriesConsumed));
        burnedCaloriesText.setText(String.valueOf(caloriesBurned));
        netCaloriesText.setText(String.valueOf(netCalories));

        calorieBalanceText.setText(getString(R.string.pr_home_net, netCalories));
        calorieBalanceText.setTextColor(getColor(R.color.black));
    }

    private void updateDate() {
        String currentDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        dateText.setText(currentDate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
        refreshToday();
    }
}
