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
import java.util.LinkedHashMap;
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

    // In CalorieCalculatorActivity.java

    private void initializeDatabases() {
        foodDatabase = new HashMap<>();

        // The first unit added via addConversion() is now the default.
        FoodItem apple = new FoodItem("Apple", 52);
        apple.addConversion("medium piece", 182); // Default
        apple.addConversion("cup, sliced", 125);
        apple.addUniversalUnits();

        FoodItem banana = new FoodItem("Banana", 89);
        banana.addConversion("medium piece", 118); // Default
        banana.addUniversalUnits();

        FoodItem strawberries = new FoodItem("Strawberries", 32);
        strawberries.addConversion("cup, whole", 152); // Default
        strawberries.addUniversalUnits();

        FoodItem avocado = new FoodItem("Avocado", 160);
        avocado.addConversion("medium", 200); // Default
        avocado.addConversion("cup, sliced", 146);
        avocado.addUniversalUnits();

        foodDatabase.put("Fruits", Arrays.asList(apple, banana, strawberries, avocado));

        FoodItem carrot = new FoodItem("Carrot", 41);
        carrot.addConversion("medium piece", 61); // Default
        carrot.addConversion("cup, chopped", 122);
        carrot.addUniversalUnits();

        FoodItem spinach = new FoodItem("Spinach", 23);
        spinach.addConversion("cup, raw", 30); // Default
        spinach.addConversion("cup, cooked", 180);
        spinach.addUniversalUnits();

        FoodItem broccoli = new FoodItem("Broccoli", 55);
        broccoli.addConversion("cup, chopped", 91); // Default
        broccoli.addUniversalUnits();

        FoodItem bellPepper = new FoodItem("Bell Pepper", 31);
        bellPepper.addConversion("medium", 119); // Default
        bellPepper.addConversion("cup, chopped", 149);
        bellPepper.addUniversalUnits();

        foodDatabase.put("Vegetables", Arrays.asList(carrot, spinach, broccoli, bellPepper));

        FoodItem egg = new FoodItem("Eggs", 155);
        egg.addConversion("large egg", 50); // Default
        egg.addUniversalUnits();

        // For items with no specific unit, 'g' will be the default.
        FoodItem chickenBreast = new FoodItem("Chicken Breast (Cooked)", 165);
        chickenBreast.addUniversalUnits(); // 'g' will be the first added, making it the default.

        FoodItem salmon = new FoodItem("Salmon (Cooked)", 208);
        salmon.addUniversalUnits();

        FoodItem beefSteak = new FoodItem("Beef Steak (Cooked)", 271);
        beefSteak.addUniversalUnits();

        FoodItem tofu = new FoodItem("Tofu (Firm)", 76);
        tofu.addConversion("cup", 252); // Default
        tofu.addUniversalUnits();

        foodDatabase.put("Proteins", Arrays.asList(egg, chickenBreast, salmon, beefSteak, tofu));

        FoodItem oats = new FoodItem("Oats (Dry)", 389);
        oats.addConversion("cup, dry", 81); // Default
        oats.addUniversalUnits();

        FoodItem bread = new FoodItem("Whole Wheat Bread", 265);
        bread.addConversion("slice", 28); // Default
        bread.addUniversalUnits();

        FoodItem whiteRice = new FoodItem("White Rice (Cooked)", 130);
        whiteRice.addConversion("cup, cooked", 158); // Default
        whiteRice.addUniversalUnits();

        FoodItem quinoa = new FoodItem("Quinoa (Cooked)", 120);
        quinoa.addConversion("cup, cooked", 185); // Default
        quinoa.addUniversalUnits();

        FoodItem pasta = new FoodItem("Pasta (Dry)", 371);
        pasta.addConversion("cup, dry", 145); // Default
        pasta.addUniversalUnits();

        foodDatabase.put("Grains", Arrays.asList(oats, bread, whiteRice, quinoa, pasta));

        FoodItem milk = new FoodItem("Milk (Whole)", 61);
        milk.addConversion("cup", 244); // Default
        milk.addConversion("tbsp", 15);
        milk.addUniversalUnits();

        FoodItem cheddarCheese = new FoodItem("Cheddar Cheese", 404);
        cheddarCheese.addConversion("slice", 28); // Default
        cheddarCheese.addConversion("cup, shredded", 113);
        cheddarCheese.addUniversalUnits();

        FoodItem greekYogurt = new FoodItem("Greek Yogurt (Plain)", 59);
        greekYogurt.addConversion("cup", 245); // Default
        greekYogurt.addUniversalUnits();

        FoodItem almondMilk = new FoodItem("Almond Milk (Unsweetened)", 17);
        almondMilk.addConversion("cup", 240); // Default
        almondMilk.addUniversalUnits();

        foodDatabase.put("Dairy & Alternatives", Arrays.asList(milk, cheddarCheese, greekYogurt, almondMilk));

        FoodItem almonds = new FoodItem("Almonds", 579);
        almonds.addConversion("oz", 28.35); // Set 'oz' as default instead of 'g'
        almonds.addConversion("cup", 143);
        almonds.addConversion("g", 1.0);
        almonds.addConversion("lb", 453.59);


        FoodItem peanutButter = new FoodItem("Peanut Butter", 588);
        peanutButter.addConversion("tbsp", 16); // Default
        peanutButter.addUniversalUnits();

        FoodItem chiaSeeds = new FoodItem("Chia Seeds", 486);
        chiaSeeds.addConversion("tbsp", 10); // Default
        chiaSeeds.addUniversalUnits();

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

        String defaultUnit = selectedItem.getDefaultUnit();
        if (defaultUnit != null) {
            // Find the position of the default unit in the adapter.
            int defaultUnitPosition = portionUnitAdapter.getPosition(defaultUnit);
            if (defaultUnitPosition >= 0) {
                // Set the spinner to automatically select the default unit.
                portionUnitSpinner.setSelection(defaultUnitPosition);
            }
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

    // In CalorieCalculatorActivity.java, at the bottom of the file

    private static class FoodItem {
        final String name;
        final double caloriesPer100g;
        // Use a LinkedHashMap to preserve the order of insertion.
        // The first unit added will be treated as the default.
        private final Map<String, Double> conversionMap = new LinkedHashMap<>();

        FoodItem(String name, double caloriesPer100g) {
            this.name = name;
            this.caloriesPer100g = caloriesPer100g;
            // Universal units are added last, so they are not the default.
        }

        public void addConversion(String unitName, double grams) {
            this.conversionMap.put(unitName, grams);
        }

        // NEW: Method to add the universal conversions after the primary ones.
        public void addUniversalUnits() {
            this.addConversion("g", 1.0);
            this.addConversion("oz", 28.35);
            this.addConversion("lb", 453.59);
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

        // NEW: Method to get the first (default) unit from the ordered map.
        public String getDefaultUnit() {
            // Return the first key in the LinkedHashMap, or null if it's empty.
            if (!conversionMap.isEmpty()) {
                return conversionMap.keySet().iterator().next();
            }
            return null;
        }
    }

}
