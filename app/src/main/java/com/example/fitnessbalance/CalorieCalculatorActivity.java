package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalorieCalculatorActivity extends AppCompatActivity {
    private ImageButton backButton;

    // Food UI
    private Spinner foodCategorySpinner, foodItemSpinner, portionUnitSpinner;
    private TextInputEditText portionSizeInput;
    private Button calculateFoodCaloriesButton, addToFoodLogButton;
    private LinearLayout foodResultLayout;
    private TextView foodCalculationText, foodCaloriesResult;

    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private Map<String, List<FoodItem>> foodDatabase;
    private double currentFoodCalories = 0;
    private String currentFoodName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calorie_calculator);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();
        setupClickListeners();
        initializeDatabases();
        setupSpinners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        foodCategorySpinner = findViewById(R.id.foodCategorySpinner);
        foodItemSpinner = findViewById(R.id.foodItemSpinner);
        portionUnitSpinner = findViewById(R.id.portionUnitSpinner);
        portionSizeInput = findViewById(R.id.portionSizeInput);
        calculateFoodCaloriesButton = findViewById(R.id.calculateFoodCaloriesButton);
        foodResultLayout = findViewById(R.id.foodResultLayout);
        foodCalculationText = findViewById(R.id.foodCalculationText);
        foodCaloriesResult = findViewById(R.id.foodCaloriesResult);
        addToFoodLogButton = findViewById(R.id.addToFoodLogButton);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        calculateFoodCaloriesButton.setOnClickListener(v -> calculateFoodCalories());
        addToFoodLogButton.setOnClickListener(v -> addToFoodLog());
    }

    private void initializeDatabases() {
        foodDatabase = new HashMap<>();
        FoodItem apple = new FoodItem("Apple", 52);
        apple.addConversion("medium piece", 182);
        apple.addConversion("cup, sliced", 125);

        FoodItem banana = new FoodItem("Banana", 89);
        banana.addConversion("medium piece", 118);

        FoodItem strawberries = new FoodItem("Strawberries", 32);
        strawberries.addConversion("cup, whole", 152);

        FoodItem avocado = new FoodItem("Avocado", 160);
        avocado.addConversion("medium", 200);
        avocado.addConversion("cup, sliced", 146);

        foodDatabase.put("Fruits", Arrays.asList(apple, banana, strawberries, avocado));

        FoodItem carrot = new FoodItem("Carrot", 41);
        carrot.addConversion("medium piece", 61);
        carrot.addConversion("cup, chopped", 122);

        FoodItem spinach = new FoodItem("Spinach", 23);
        spinach.addConversion("cup, raw", 30);
        spinach.addConversion("cup, cooked", 180);

        FoodItem broccoli = new FoodItem("Broccoli", 55);
        broccoli.addConversion("cup, chopped", 91);

        FoodItem bellPepper = new FoodItem("Bell Pepper", 31);
        bellPepper.addConversion("medium", 119);
        bellPepper.addConversion("cup, chopped", 149);

        foodDatabase.put("Vegetables", Arrays.asList(carrot, spinach, broccoli, bellPepper));
        FoodItem egg = new FoodItem("Eggs", 155);
        egg.addConversion("large egg", 50);

        FoodItem chickenBreast = new FoodItem("Chicken Breast (Cooked)", 165);

        FoodItem salmon = new FoodItem("Salmon (Cooked)", 208);

        FoodItem beefSteak = new FoodItem("Beef Steak (Cooked)", 271);

        FoodItem tofu = new FoodItem("Tofu (Firm)", 76);
        tofu.addConversion("cup", 252);

        foodDatabase.put("Proteins", Arrays.asList(egg, chickenBreast, salmon, beefSteak, tofu));

        FoodItem oats = new FoodItem("Oats (Dry)", 389);
        oats.addConversion("cup, dry", 81);

        FoodItem bread = new FoodItem("Whole Wheat Bread", 265);
        bread.addConversion("slice", 28);

        FoodItem whiteRice = new FoodItem("White Rice (Cooked)", 130);
        whiteRice.addConversion("cup, cooked", 158);

        FoodItem quinoa = new FoodItem("Quinoa (Cooked)", 120);
        quinoa.addConversion("cup, cooked", 185);

        FoodItem pasta = new FoodItem("Pasta (Dry)", 371);
        pasta.addConversion("cup, dry", 145);

        foodDatabase.put("Grains", Arrays.asList(oats, bread, whiteRice, quinoa, pasta));

        FoodItem milk = new FoodItem("Milk (Whole)", 61);
        milk.addConversion("cup", 244);
        milk.addConversion("tbsp", 15);

        FoodItem cheddarCheese = new FoodItem("Cheddar Cheese", 404);
        cheddarCheese.addConversion("slice", 28);
        cheddarCheese.addConversion("cup, shredded", 113);

        FoodItem greekYogurt = new FoodItem("Greek Yogurt (Plain)", 59);
        greekYogurt.addConversion("cup", 245);

        FoodItem almondMilk = new FoodItem("Almond Milk (Unsweetened)", 17);
        almondMilk.addConversion("cup", 240);

        foodDatabase.put("Dairy & Alternatives", Arrays.asList(milk, cheddarCheese, greekYogurt, almondMilk));
        FoodItem almonds = new FoodItem("Almonds", 579);
        almonds.addConversion("cup", 143);

        FoodItem peanutButter = new FoodItem("Peanut Butter", 588);
        peanutButter.addConversion("tbsp", 16);

        FoodItem chiaSeeds = new FoodItem("Chia Seeds", 486);
        chiaSeeds.addConversion("tbsp", 10);

        foodDatabase.put("Nuts & Seeds", Arrays.asList(almonds, peanutButter, chiaSeeds));
    }

    private void setupSpinners() {
        List<String> categories = new ArrayList<>(foodDatabase.keySet());
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodCategorySpinner.setAdapter(categoryAdapter);

        AdapterView.OnItemSelectedListener selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent.getId() == R.id.foodCategorySpinner) {
                    updateFoodItemsSpinner();
                }
                updatePortionUnitSpinner();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        foodCategorySpinner.setOnItemSelectedListener(selectionListener);
        foodItemSpinner.setOnItemSelectedListener(selectionListener);

        // Initial setup
        updateFoodItemsSpinner();
    }

    private void updateFoodItemsSpinner() {
        String selectedCategory = foodCategorySpinner.getSelectedItem().toString();
        List<FoodItem> foodItems = foodDatabase.get(selectedCategory);
        if (foodItems == null) return;

        List<String> foodNames = new ArrayList<>();
        for (FoodItem item : foodItems) {
            foodNames.add(item.name);
        }
        ArrayAdapter<String> foodItemAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, foodNames);
        foodItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodItemSpinner.setAdapter(foodItemAdapter);
    }

    private void updatePortionUnitSpinner() {
        FoodItem selectedItem = findSelectedFoodItem();
        if (selectedItem == null) {
            portionUnitSpinner.setAdapter(null);
            return;
        }
        List<String> units = selectedItem.getAvailableUnits();
        ArrayAdapter<String> portionUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        portionUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        portionUnitSpinner.setAdapter(portionUnitAdapter);
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
                portionSizeInput.setError("Portion must be positive");
                return;
            }

            FoodItem selectedItem = findSelectedFoodItem();
            if (selectedItem != null && portionUnitSpinner.getSelectedItem() != null) {
                String portionUnit = portionUnitSpinner.getSelectedItem().toString();
                double totalGrams = selectedItem.convertToGrams(portionSize, portionUnit);

                if (totalGrams < 0) {
                    Toast.makeText(this, "Cannot convert " + portionUnit + " for " + selectedItem.name, Toast.LENGTH_SHORT).show();
                    return;
                }

                currentFoodCalories = (totalGrams / 100.0) * selectedItem.caloriesPer100g;
                currentFoodName = selectedItem.name;
                foodCalculationText.setText(String.format(Locale.getDefault(),
                        "%.1f %s of %s", portionSize, portionUnit, currentFoodName)); // Use %.1f for better display
                foodCaloriesResult.setText(String.format(Locale.getDefault(), "%.0f calories", currentFoodCalories));
                foodResultLayout.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Please select a food and unit", Toast.LENGTH_SHORT).show();
            }

        } catch (NumberFormatException e) {
            portionSizeInput.setError("Invalid number format");
        }
    }

    private void addToFoodLog() {
        if (currentFoodCalories > 0 && !TextUtils.isEmpty(currentFoodName)) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("logType", "food"); // Identify the log type
            resultIntent.putExtra("itemName", currentFoodName);
            resultIntent.putExtra("calories", (int) currentFoodCalories);
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Calculate food calories first", Toast.LENGTH_SHORT).show();
        }
    }

    private FoodItem findSelectedFoodItem() {
        if (foodCategorySpinner.getSelectedItem() == null || foodItemSpinner.getSelectedItem() == null) {
            return null;
        }
        String category = foodCategorySpinner.getSelectedItem().toString();
        String name = foodItemSpinner.getSelectedItem().toString();
        return findFoodItem(category, name);
    }

    private FoodItem findFoodItem(String category, String name) {
        List<FoodItem> items = foodDatabase.get(category);
        if (items != null) {
            for (FoodItem item : items) {
                if (item.name.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }

    private static class FoodItem {
        final String name;
        final double caloriesPer100g;
        private final Map<String, Double> conversionMap = new HashMap<>();

        FoodItem(String name, double caloriesPer100g) {
            this.name = name;
            this.caloriesPer100g = caloriesPer100g;
            this.addConversion("g", 1.0);
            this.addConversion("oz", 28.35);
            this.addConversion("lb", 453.59);
        }

        public void addConversion(String unitName, double grams) {
            this.conversionMap.put(unitName, grams);
        }

        public double convertToGrams(double amount, String unit) {
            if (conversionMap.containsKey(unit)) {
                Double conversionFactor = conversionMap.get(unit);
                if (conversionFactor != null) {
                    return amount * conversionFactor;
                }
            }
            return -1;
        }

        public List<String> getAvailableUnits() {
            return new ArrayList<>(conversionMap.keySet());
        }
    }
}
