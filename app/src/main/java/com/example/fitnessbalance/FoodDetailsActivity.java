package com.example.fitnessbalance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FoodDetailsActivity extends AppCompatActivity {

    private EditText portion, servings, manualCalories;
    private Spinner meal, unit, barcodeReferenceUnit;
    private CheckBox manual, barcodeConfirmed;
    private TextView status, nutrients, totalAmount;
    private Button logButton, retry;

    private Map<String, Object> nutrition = Collections.emptyMap();
    private Map<String, Object> food;

    private String foodId, barcode;
    private String date, entryId, basis, restoredUnit;

    private boolean saving;
    private boolean barcodeManualFallbackActive;

    private final ExecutorService barcodeExecutor =
            Executors.newSingleThreadExecutor();

    private Future<?> barcodeRequest;

    private boolean checkingSavedProduct;
    private int barcodeLookupVersion;

    private final ActivityResultLauncher<Intent> productEditor =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            barcodeManualFallbackActive = false;
                            food = null;

                            manual.setChecked(false);
                            barcodeConfirmed.setChecked(false);

                            lookupBarcode();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_food_details);
        FoodUi.insets(this);

        portion = findViewById(R.id.foodPortion);
        servings = findViewById(R.id.foodServings);
        manualCalories = findViewById(R.id.foodManualCalories);
        meal = findViewById(R.id.foodMeal);
        unit = findViewById(R.id.foodUnit);
        manual = findViewById(R.id.foodManual);
        status = findViewById(R.id.foodDetailsStatus);
        nutrients = findViewById(R.id.foodNutrients);
        totalAmount = findViewById(R.id.foodTotalAmount);
        logButton = findViewById(R.id.foodLogButton);
        retry = findViewById(R.id.foodRetry);

        barcode = getIntent().getStringExtra("barcode");

        findViewById(R.id.barcodeSaveProduct).setVisibility(
                barcode != null ? View.VISIBLE : View.GONE
        );

        findViewById(R.id.barcodeSaveProduct).setOnClickListener(v -> {
            if (!BarcodeFood.validBarcode(barcode)) return;

            // Ignore an older lookup response after editing the product.
            barcodeLookupVersion++;
            checkingSavedProduct = false;

            if (barcodeRequest != null) {
                barcodeRequest.cancel(true);
            }

            barcodeRequest = null;

            Intent intent = new Intent(
                    this,
                    CreateBarcodeFoodActivity.class
            );

            intent.putExtra("barcode", barcode);
            productEditor.launch(intent);
        });

        barcodeManualFallbackActive = state != null
                && state.getBoolean("barcodeManualFallbackActive");

        barcodeConfirmed = findViewById(R.id.barcodeConfirmed);
        barcodeReferenceUnit =
                findViewById(R.id.barcodeReferenceUnit);

        ArrayAdapter<String> referenceAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        new String[]{"g", "ml"}
                );

        referenceAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        barcodeReferenceUnit.setAdapter(referenceAdapter);

        barcodeReferenceUnit.setSelection(
                state != null
                        && "ml".equals(
                        state.getString("barcodeReferenceUnit")
                ) ? 1 : 0
        );

        barcodeConfirmed.setChecked(
                state != null && state.getBoolean("barcodeConfirmed")
        );

        barcodeConfirmed.setOnCheckedChangeListener(
                (button, checked) -> applyBarcodeBasis()
        );

        barcodeReferenceUnit.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        applyBarcodeBasis();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {}
                }
        );

        findViewById(R.id.barcodeManualFallback)
                .setOnClickListener(v -> manualBarcode());

        findViewById(R.id.barcodeAttribution)
                .setOnClickListener(v -> {
                    if (!BarcodeFood.validBarcode(barcode)) return;

                    try {
                        startActivity(new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                        "https://world.openfoodfacts.org/product/"
                                                + barcode
                                )
                        ));
                    } catch (
                            android.content.ActivityNotFoundException ignored
                    ) {
                        // No browser installed on this device.
                    }
                });

        date = getIntent().getStringExtra("date");

        if (!FoodUi.validDate(date)) {
            date = FoodUi.date(new Date());
        }

        entryId = state == null
                ? UUID.randomUUID().toString()
                : state.getString("entryId");

        if (entryId == null) {
            entryId = UUID.randomUUID().toString();
        }

        portion.setText(state == null
                ? "100" : state.getString("portion", "100"));

        servings.setText(state == null
                ? "1" : state.getString("servings", "1"));

        manualCalories.setText(state == null
                ? "" : state.getString("manualCalories", ""));

        manual.setChecked(
                state != null && state.getBoolean("manual")
        );

        meal.setSelection(state == null
                ? 0
                : Math.max(0, Math.min(3, state.getInt("meal"))));

        restoredUnit = state == null
                ? null : state.getString("unit");

        TextView dateText = findViewById(R.id.foodDetailsDate);
        dateText.setText(getString(R.string.fl_date, date));

        findViewById(R.id.foodBack)
                .setOnClickListener(v -> finish());

        logButton.setOnClickListener(v -> save());
        retry.setOnClickListener(v -> loadFood());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                updatePortion();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };

        portion.addTextChangedListener(watcher);
        servings.addTextChangedListener(watcher);
        manualCalories.addTextChangedListener(watcher);

        manual.setOnCheckedChangeListener(
                (button, checked) -> configureUnits()
        );

        unit.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        updatePortion();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {}
                }
        );

        loadFood();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(
                "barcodeManualFallbackActive",
                barcodeManualFallbackActive
        );
        state.putBoolean(
                "barcodeConfirmed",
                barcodeConfirmed.isChecked()
        );
        state.putString(
                "barcodeReferenceUnit",
                barcodeReferenceUnit.getSelectedItem().toString()
        );
        state.putString("entryId", entryId);
        state.putString("portion", portion.getText().toString());
        state.putString("servings", servings.getText().toString());
        state.putString(
                "manualCalories",
                manualCalories.getText().toString()
        );
        state.putBoolean("manual", manual.isChecked());
        state.putInt("meal", meal.getSelectedItemPosition());
        state.putString("unit", selectedUnit());

        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (barcodeRequest != null) {
            barcodeRequest.cancel(true);
        }

        barcodeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadFood() {
        if (barcode != null) {
            if (barcodeManualFallbackActive) {
                manualBarcode();
            } else {
                lookupBarcode();
            }
            return;
        }

        String id = getIntent().getStringExtra("foodId");

        if (id == null || id.isEmpty() || id.contains("/")) {
            status.setText(R.string.fl_food_missing);
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            status.setText(R.string.fl_sign_in);
            return;
        }

        retry.setVisibility(View.GONE);
        status.setText(R.string.fl_loading);
        logButton.setEnabled(false);

        FirebaseFirestore.getInstance()
                .collection("foods")
                .document(id)
                .get()
                .addOnSuccessListener(document -> {
                    if (isDestroyed() || isFinishing()) return;

                    if (!document.exists()) {
                        status.setText(R.string.fl_food_missing);
                        return;
                    }

                    showFood(
                            document.getData(),
                            document.getId()
                    );
                })
                .addOnFailureListener(error -> {
                    if (isDestroyed() || isFinishing()) return;

                    status.setText(FoodUi.error(
                            this,
                            R.string.fl_load_operation,
                            error
                    ));

                    retry.setVisibility(View.VISIBLE);
                });
    }

    private void lookupBarcode() {
        if (!BarcodeFood.validBarcode(barcode)) {
            status.setText(R.string.bc_invalid);
            return;
        }

        if (checkingSavedProduct
                || (barcodeRequest != null
                && !barcodeRequest.isDone())) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            status.setText(R.string.fl_sign_in);
            return;
        }

        final int version = ++barcodeLookupVersion;

        checkingSavedProduct = true;
        status.setText(R.string.sb_loading);
        logButton.setEnabled(false);
        retry.setVisibility(View.GONE);

        findViewById(R.id.barcodeManualFallback)
                .setVisibility(View.GONE);

        SavedBarcodeFoods.reference(user.getUid(), barcode)
                .get()
                .addOnSuccessListener(document -> {
                    if (version != barcodeLookupVersion) return;

                    checkingSavedProduct = false;

                    if (isDestroyed() || isFinishing()) return;

                    FirebaseUser current =
                            FirebaseAuth.getInstance().getCurrentUser();

                    if (current == null
                            || !user.getUid().equals(current.getUid())) {
                        status.setText(R.string.fl_sign_in);
                        return;
                    }

                    if (document.exists()) {
                        if (!barcode.equals(
                                document.getString("barcode")
                        )) {
                            lookupFailed(R.string.sb_invalid);
                            return;
                        }

                        showFood(
                                document.getData(),
                                "user_barcode_" + barcode
                        );
                    } else {
                        lookupRemoteBarcode(version);
                    }
                })
                .addOnFailureListener(error -> {
                    if (version != barcodeLookupVersion) return;

                    checkingSavedProduct = false;

                    if (isDestroyed() || isFinishing()) return;

                    android.util.Log.e(
                            "FoodLog",
                            "Saved product lookup failed",
                            error
                    );

                    lookupFailed(R.string.sb_access);
                });
    }

    private void lookupRemoteBarcode(int version) {
        if (!BarcodeFood.validBarcode(barcode)) {
            status.setText(R.string.bc_invalid);
            return;
        }

        if (barcodeRequest != null && !barcodeRequest.isDone()) {
            return;
        }

        status.setText(R.string.bc_looking);
        retry.setVisibility(View.GONE);

        findViewById(R.id.barcodeManualFallback)
                .setVisibility(View.GONE);

        logButton.setEnabled(false);

        barcodeRequest = barcodeExecutor.submit(() -> {
            try {
                Map<String, Object> product =
                        BarcodeFoodClient.lookup(
                                barcode,
                                getString(R.string.bc_http_agent)
                        );

                runOnUiThread(() -> {
                    if (version != barcodeLookupVersion
                            || isDestroyed()
                            || isFinishing()) {
                        return;
                    }

                    if (product == null) {
                        lookupFailed(R.string.bc_not_found);
                        return;
                    }

                    showFood(product, "off_" + barcode);
                });

            } catch (Exception error) {
                android.util.Log.e(
                        "FoodLog",
                        "Product lookup failed",
                        error
                );

                runOnUiThread(() -> {
                    if (version != barcodeLookupVersion
                            || isDestroyed()
                            || isFinishing()) {
                        return;
                    }

                    lookupFailed(
                            error instanceof BarcodeFoodClient.RateLimited
                                    ? R.string.bc_rate_limit
                                    : R.string.bc_lookup_failed
                    );
                });
            }
        });
    }

    private void lookupFailed(int message) {
        status.setText(message);
        retry.setVisibility(View.VISIBLE);

        findViewById(R.id.barcodeManualFallback)
                .setVisibility(View.VISIBLE);
    }

    private void manualBarcode() {
        barcodeManualFallbackActive = true;

        Map<String, Object> product = new HashMap<>();

        product.put(
                "food_name",
                getString(R.string.bc_product, barcode)
        );
        product.put("barcode", barcode);
        product.put(
                "source_database",
                getString(R.string.fs_user_entered)
        );
        product.put("nutrition_basis", "unknown");
        product.put("nutrition", Collections.emptyMap());

        manual.setChecked(true);

        showFood(product, "off_" + barcode);
    }

    @SuppressWarnings("unchecked")
    private void showFood(
            Map<String, Object> data,
            String id
    ) {
        food = new HashMap<>(data);
        foodId = id;

        Object raw = food.get("nutrition");

        nutrition = raw instanceof Map
                ? new HashMap<>((Map<String, Object>) raw)
                : Collections.emptyMap();

        basis = text("nutrition_basis");

        TextView nameText = findViewById(R.id.foodDetailsName);
        nameText.setText(text("food_name") == null
                ? getString(R.string.fl_name_missing)
                : text("food_name"));

        Object source = food.get("source_database");

        TextView sourceText = findViewById(R.id.foodDetailsSource);
        sourceText.setText(getString(
                R.string.fl_source,
                source == null ? foodId : source.toString()
        ));

        TextView referenceText =
                findViewById(R.id.foodReferenceNutrients);

        referenceText.setText(
                FoodUi.nutrients(this, nutrition, null, 0)
        );

        retry.setVisibility(View.GONE);

        findViewById(R.id.barcodeManualFallback)
                .setVisibility(View.GONE);

        showExtraFields(food);

        // Open the existing manual form rather than leaving an unexplained disabled button.
        // Barcode records may still have an explicit package-basis confirmation flow.
        if ((!requiresBasisConfirmation() && FoodNutrition.portionUnit(basis) == null)
                || FoodNutrition.number(
                        FoodNutrition.withEstimatedEnergy(nutrition).get("energy_kcal")
                ) == null) {
            manual.setChecked(true);
        }

        findViewById(R.id.barcodeConfirmPanel).setVisibility(
                requiresBasisConfirmation()
                        ? View.VISIBLE : View.GONE
        );

        if (requiresBasisConfirmation()) {
            applyBarcodeBasis();
        } else {
            configureUnits();
        }
    }

    private String text(String key) {
        Object value = food.get(key);
        return value instanceof String ? (String) value : null;
    }

    private boolean requiresBasisConfirmation() {
        return food != null && Boolean.TRUE.equals(
                food.get("basis_confirmation_required")
        );
    }

    private void applyBarcodeBasis() {
        if (!requiresBasisConfirmation() || saving) return;

        String confirmedUnit =
                barcodeReferenceUnit.getSelectedItem().toString();

        basis = barcodeConfirmed.isChecked()
                ? "per_100" + confirmedUnit
                : "unknown";

        configureUnits();
    }

    private String selectedUnit() {
        return unit.getSelectedItem() == null
                ? "g" : unit.getSelectedItem().toString();
    }

    private void configureUnits() {
        if (food == null || saving) return;

        String reference = FoodNutrition.portionUnit(basis);

        String previous = restoredUnit != null
                ? restoredUnit : selectedUnit();

        restoredUnit = null;

        String[] choices =
                !manual.isChecked() && reference != null
                        ? new String[]{reference}
                        : new String[]{"g", "ml"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                choices
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        unit.setAdapter(adapter);

        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equals(previous)) {
                unit.setSelection(i);
            }
        }

        findViewById(R.id.foodManualPanel).setVisibility(
                manual.isChecked() ? View.VISIBLE : View.GONE
        );

        TextView basisText = findViewById(R.id.foodBasis);

        if (reference == null) {
            basisText.setText(requiresBasisConfirmation()
                    ? R.string.bc_needs_confirmation
                    : R.string.fs_basis_unknown);
        } else {
            basisText.setText(getString(
                    requiresBasisConfirmation()
                            ? R.string.bc_confirmed_basis
                            : R.string.fs_basis_known,
                    reference
            ));
        }

        updatePortion();
    }

    private double value(EditText input) {
        try {
            return Double.parseDouble(
                    input.getText().toString()
                            .trim()
                            .replace(',', '.')
            );
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private Map<String, Object> calculated() {
        FoodNutrition.totalAmount(
                value(portion),
                value(servings)
        );

        if (manual.isChecked()) {
            return FoodNutrition.manualNutrition(
                    value(manualCalories),
                    value(servings)
            );
        }

        return FoodNutrition.servingNutrition(
                nutrition,
                basis,
                selectedUnit(),
                value(portion),
                value(servings)
        );
    }

    private void updatePortion() {
        if (food == null || saving) return;

        logButton.setEnabled(false);
        status.setText("");
        totalAmount.setText("");

        nutrients.setText(FoodUi.nutrients(
                this,
                Collections.emptyMap(),
                null,
                0
        ));

        if (!manual.isChecked()
                && FoodNutrition.portionUnit(basis) == null) {
            status.setText(requiresBasisConfirmation()
                    ? R.string.bc_needs_confirmation
                    : R.string.fs_basis_unknown);
            return;
        }

        try {
            Map<String, Object> scaled = calculated();

            totalAmount.setText(getString(
                    R.string.fs_amount,
                    FoodUi.number(FoodNutrition.totalAmount(
                            value(portion),
                            value(servings)
                    )),
                    selectedUnit()
            ));

            nutrients.setText(
                    FoodUi.nutrients(this, scaled, null, 0)
            );

            if (FoodNutrition.number(
                    scaled.get("energy_kcal")
            ) == null) {
                status.setText(R.string.fs_calorie_missing);
                return;
            }

            logButton.setEnabled(true);

            if (manual.isChecked()) {
                status.setText(R.string.fc_manual_ready);
            } else if (FoodNutrition.usesEstimatedEnergy(nutrition)) {
                status.setText(R.string.fc_estimate);
            }

        } catch (IllegalArgumentException invalid) {
            status.setText(manual.isChecked()
                    && !Double.isFinite(value(manualCalories))
                    ? R.string.fc_manual_needed : R.string.fs_invalid);
        }
    }

    private void showExtraFields(
            Map<String, Object> document
    ) {
        Map<String, Object> extra = new TreeMap<>(nutrition);

        for (String key : FoodNutrition.KEYS) {
            extra.remove(key);
        }

        Object additional = document.get("additional_nutrients");

        if (additional instanceof Map) {
            for (Map.Entry<?, ?> field :
                    ((Map<?, ?>) additional).entrySet()) {
                extra.put(
                        String.valueOf(field.getKey()),
                        field.getValue()
                );
            }
        }

        for (Map.Entry<String, Object> field :
                document.entrySet()) {
            if (field.getKey().matches(
                    ".*\\((g|mg|µg|μg|ug|kcal|kJ|ml)\\).*"
            )) {
                extra.put(field.getKey(), field.getValue());
            }
        }

        findViewById(R.id.foodExtraPanel).setVisibility(
                extra.isEmpty() ? View.GONE : View.VISIBLE
        );

        StringBuilder text = new StringBuilder();

        for (Map.Entry<String, Object> entry : extra.entrySet()) {
            text.append(entry.getKey().replace('_', ' '))
                    .append(": ")
                    .append(entry.getValue() == null
                            ? getString(R.string.fl_missing)
                            : entry.getValue())
                    .append('\n');
        }

        TextView extraText = findViewById(R.id.foodExtra);
        extraText.setText(text.toString().trim());
    }

    private void controls(boolean enabled) {
        findViewById(R.id.barcodeSaveProduct).setEnabled(enabled);
        portion.setEnabled(enabled);
        servings.setEnabled(enabled);
        manualCalories.setEnabled(enabled);
        unit.setEnabled(enabled);
        meal.setEnabled(enabled);
        manual.setEnabled(enabled);
        barcodeConfirmed.setEnabled(enabled);
        barcodeReferenceUnit.setEnabled(enabled);
    }

    private void save() {
        if (saving || food == null) return;

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            status.setText(R.string.fl_sign_in);
            return;
        }

        final Map<String, Object> scaled;

        try {
            scaled = calculated();
        } catch (IllegalArgumentException invalid) {
            updatePortion();
            return;
        }

        if (FoodNutrition.number(
                scaled.get("energy_kcal")
        ) == null) {
            return;
        }

        double amount = FoodNutrition.totalAmount(
                value(portion),
                value(servings)
        );

        Map<String, Object> entry = new HashMap<>();

        entry.put("entryId", entryId);
        entry.put("foodId", foodId);
        entry.put("name", text("food_name") == null
                ? getString(R.string.fl_name_missing)
                : text("food_name"));

        entry.put("meal", meal.getSelectedItem().toString());
        entry.put("servingAmount", value(portion));
        entry.put("servings", value(servings));
        entry.put("portionUnit", selectedUnit());
        entry.put("totalAmount", amount);

        if ("g".equals(selectedUnit())) {
            entry.put("portionGrams", amount);
        } else {
            entry.put("portionMillilitres", amount);
        }

        entry.put("nutritionBasis", basis);
        entry.put("sourceDatabase", text("source_database"));

        if (barcode != null) {
            entry.put("barcode", barcode);
        }

        if (food.containsKey("source_url")) {
            entry.put("sourceUrl", food.get("source_url"));
        }

        if (food.containsKey("retrieved_at")) {
            entry.put(
                    "sourceRetrievedAt",
                    food.get("retrieved_at")
            );
        }

        entry.put(
                "basisConfirmationSource",
                manual.isChecked()
                        ? "manual_entry"
                        : "user_entered".equals(
                        food.get("nutrition_verification")
                ) || (requiresBasisConfirmation()
                        && barcodeConfirmed.isChecked())
                        ? "user_package_label"
                        : "source_metadata"
        );

        entry.put(
                "calculationMode",
                manual.isChecked() ? "manual"
                        : FoodNutrition.usesEstimatedEnergy(nutrition)
                        ? "macro_estimate" : "database"
        );
        entry.put("energyMethod", manual.isChecked() ? "user_entered"
                : FoodNutrition.usesEstimatedEnergy(nutrition)
                ? "atwater_4_4_9" : "source_reported");

        entry.put(
                "nutritionVerification",
                manual.isChecked()
                        || "user_entered".equals(
                        food.get("nutrition_verification")
                )
                        ? "user_entered"
                        : "not_independently_verified"
        );

        if (manual.isChecked()) {
            entry.put(
                    "manualCaloriesPerServing",
                    value(manualCalories)
            );
        }

        if (food.containsKey("updated_at")) {
            entry.put("sourceUpdatedAt", food.get("updated_at"));
        }

        entry.put("nutrients", scaled);
        entry.put("calories", scaled.get("energy_kcal"));
        entry.put("loggedAt", new Date());

        saving = true;
        controls(false);
        logButton.setEnabled(false);
        status.setText(R.string.fl_saving);

        FoodLogRepository.save(
                user.getUid(),
                date,
                entry,
                false
        ).addOnSuccessListener(unused -> {
            if (isDestroyed() || isFinishing()) return;

            Toast.makeText(
                    this,
                    R.string.fl_saved,
                    Toast.LENGTH_SHORT
            ).show();

            finish();

        }).addOnFailureListener(error -> {
            if (isDestroyed() || isFinishing()) return;

            saving = false;
            controls(true);
            updatePortion();

            status.setText(FoodUi.error(
                    this,
                    R.string.fl_save_operation,
                    error
            ));
        });
    }
}
