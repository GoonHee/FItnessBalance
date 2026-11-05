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
            todayNutrition=null;renderNutrition(null);
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
            todayNutrition=null;renderNutrition(null);
        }
    }

    private void updateWelcomeMessage() {
        if (userName != null && !userName.isEmpty()) {
            welcomeText.setText("Hello, " + userName + "!");
        } else {
            welcomeText.setText("Welcome to FitnessBalance!");
        }
    }

    private NutritionAvailabilityService.Stats todayNutrition;
    private void loadTodayMetrics() {
        final int token=++metricsRequest;
        if(currentUser==null){todayNutrition=null;renderNutrition(null);return;}
        String userId=currentUser.getUid(),day=com.example.fitnessbalance.util.LocalDayObserver.today();
        com.google.firebase.firestore.DocumentReference user=firestore.collection("users").document(userId);
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> daily=user.collection("dailyData").document(day).get();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> foods=user.collection("foodLogs").document(day).get();
        com.google.android.gms.tasks.Tasks.whenAll(daily,foods).addOnSuccessListener(this,ignored->{
            if(!currentMetrics(token,day,userId))return;
            try{
                com.google.firebase.firestore.DocumentSnapshot log=foods.getResult(),dashboard=daily.getResult();
                todayNutrition=log.contains("foodItems")?NutritionAvailabilityService.stats(FoodLogRepository.entries(log)):
                    NutritionAvailabilityService.dashboard(dashboard.getData()==null?java.util.Collections.emptyMap():dashboard.getData());
                if(!log.contains("foodItems")&&FoodNutrition.number(log.get("totalCalories"))!=null&&FoodNutrition.number(log.get("totalCalories"))>0)todayNutrition=NutritionAvailabilityService.legacy(log.get("totalCalories"));
                Object burned=dashboard.get("caloriesBurned");renderNutrition(burned instanceof Number?((Number)burned).intValue():0);
                refreshExerciseTotals(day,userId,token);
            }catch(RuntimeException invalid){todayNutrition=null;renderNutrition(null);}
        }).addOnFailureListener(this,error->{if(currentMetrics(token,day,userId)){todayNutrition=null;renderNutrition(null);}});
    }
    private void refreshExerciseTotals(String day,String userId,int token){
        new com.example.fitnessbalance.repository.ExerciseLogRepository(userId).loadDay(day).addOnSuccessListener(this,result->{
            if(currentMetrics(token,day,userId))renderNutrition(com.example.fitnessbalance.util.Values.integer(result.totals.get("caloriesBurned"),0));
        }).addOnFailureListener(this,error->Log.w("MainActivity","Exercise totals could not be refreshed"));
    }
    private void renderNutrition(Integer burned){
        burnedCaloriesText.setText(burned==null?"—":String.valueOf(burned));
        if(todayNutrition==null){consumedCaloriesText.setText("—");netCaloriesText.setText("—");calorieBalanceText.setText(R.string.na_load_failed);return;}
        NutritionAvailabilityService.Stats s=todayNutrition;
        consumedCaloriesText.setText(s.subtotal==null?"—":s.complete()?FoodUi.number(s.subtotal):getString(R.string.na_partial_value,FoodUi.number(s.subtotal)));
        Double net=burned==null?null:s.net(burned);netCaloriesText.setText(net==null?"—":FoodUi.number(net));
        if(!s.coverageKnown)calorieBalanceText.setText(R.string.na_home_legacy);
        else if(s.missing>0)calorieBalanceText.setText(s.known==0?getString(R.string.na_all_unknown,s.missing):getString(R.string.na_known,FoodUi.number(s.subtotal),s.missing));
        else calorieBalanceText.setText(net==null?getString(R.string.na_load_failed):getString(R.string.pr_home_net,Math.round(net)));
        calorieBalanceText.setTextColor(getColor(R.color.black));
    }

    private void updateDate() {
        String currentDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        dateText.setText(currentDate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser active = FirebaseAuth.getInstance().getCurrentUser();
        if (active == null || !active.isEmailVerified()) { AuthGateActivity.open(this); return; }
        loadUserData();
        refreshToday();
    }
}
