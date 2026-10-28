package com.example.fitnessbalance;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class CreateBarcodeFoodActivity extends AppCompatActivity {

    private EditText name, reference;

    private final EditText[] nutrientInputs =
            new EditText[FoodNutrition.KEYS.length];

    private Spinner unit;
    private CheckBox confirmation;
    private Button save, retry;
    private TextView status;

    private String barcode, uid;
    private boolean saving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView root = new ScrollView(this);
        root.setId(R.id.foodRoot);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        int padding = (int) (
                20 * getResources().getDisplayMetrics().density
        );

        content.setPadding(
                padding, padding, padding, padding
        );

        root.addView(content);
        setContentView(root);
        FoodUi.insets(this);

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setOnClickListener(v -> finish());
        content.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.sb_title);
        title.setTextSize(24);
        content.addView(title);

        barcode = getIntent().getStringExtra("barcode");

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        status = new TextView(this);
        status.setAccessibilityLiveRegion(
                View.ACCESSIBILITY_LIVE_REGION_POLITE
        );
        content.addView(status);

        if (!BarcodeFood.validBarcode(barcode) || user == null) {
            status.setText(user == null
                    ? R.string.fl_sign_in
                    : R.string.bc_invalid);
            return;
        }

        uid = user.getUid();

        TextView code = new TextView(this);
        code.setText(getString(R.string.sb_code, barcode));
        content.addView(code);

        name = input(content, R.string.sb_name, false);
        reference = input(content, R.string.sb_reference, true);

        TextView unitLabel = new TextView(this);
        unitLabel.setText(R.string.sb_unit);
        content.addView(unitLabel);

        unit = new Spinner(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"g", "ml"}
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        unit.setAdapter(adapter);
        content.addView(unit);

        TextView note = new TextView(this);
        note.setText(R.string.sb_note);
        content.addView(note);

        int[] labels = {
                R.string.fl_nutrient_energy_kcal,
                R.string.fl_nutrient_protein_g,
                R.string.fl_nutrient_carbohydrate_g,
                R.string.fl_nutrient_fat_g,
                R.string.fl_nutrient_fiber_g,
                R.string.fl_nutrient_sodium_mg
        };

        for (int i = 0; i < labels.length; i++) {
            nutrientInputs[i] = input(
                    content, labels[i], true
            );
        }

        confirmation = new CheckBox(this);
        confirmation.setText(R.string.sb_confirm);
        content.addView(confirmation);

        save = new Button(this);
        save.setText(R.string.sb_save);
        content.addView(save);
        save.setOnClickListener(v -> saveProduct());

        retry = new Button(this);
        retry.setText(R.string.fl_retry);
        retry.setVisibility(View.GONE);
        content.addView(retry);
        retry.setOnClickListener(v -> loadExisting());

        if (state != null) {
            name.setText(state.getString("name", ""));
            reference.setText(
                    state.getString("reference", "100")
            );

            unit.setSelection(
                    "ml".equals(state.getString("unit")) ? 1 : 0
            );

            for (int i = 0; i < nutrientInputs.length; i++) {
                nutrientInputs[i].setText(
                        state.getString("nutrient" + i, "")
                );
            }

            confirmation.setChecked(
                    state.getBoolean("confirmed")
            );
        } else {
            reference.setText("100");
            loadExisting();
        }
    }

    private EditText input(
            LinearLayout parent,
            int label,
            boolean numeric
    ) {
        TextView text = new TextView(this);
        text.setText(label);
        parent.addView(text);

        EditText input = new EditText(this);
        input.setContentDescription(getString(label));

        input.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT);

        input.setSaveEnabled(false);
        parent.addView(input);

        return input;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (name != null) {
            state.putString("name", name.getText().toString());

            state.putString(
                    "reference",
                    reference.getText().toString()
            );

            state.putString(
                    "unit",
                    unit.getSelectedItem().toString()
            );

            state.putBoolean(
                    "confirmed",
                    confirmation.isChecked()
            );

            for (int i = 0; i < nutrientInputs.length; i++) {
                state.putString(
                        "nutrient" + i,
                        nutrientInputs[i].getText().toString()
                );
            }
        }

        super.onSaveInstanceState(state);
    }

    private void controls(boolean enabled) {
        name.setEnabled(enabled);
        reference.setEnabled(enabled);
        unit.setEnabled(enabled);

        for (EditText input : nutrientInputs) {
            input.setEnabled(enabled);
        }

        confirmation.setEnabled(enabled);
        save.setEnabled(enabled);
    }

    private void loadExisting() {
        controls(false);
        status.setText(R.string.sb_loading);
        retry.setVisibility(View.GONE);

        SavedBarcodeFoods.reference(uid, barcode)
                .get()
                .addOnSuccessListener(document -> {
                    if (isDestroyed() || isFinishing()) return;

                    controls(true);
                    status.setText("");

                    if (!document.exists()) return;

                    Map<String, Object> food =
                            document.getData();

                    Object label = food.get("label_nutrition");

                    if (!(label instanceof Map)) {
                        status.setText(R.string.sb_invalid);
                        return;
                    }

                    Object savedName = food.get("food_name");

                    name.setText(savedName instanceof String
                            ? (String) savedName : "");

                    reference.setText(
                            food.get("label_amount") == null
                                    ? "100"
                                    : food.get("label_amount").toString()
                    );

                    unit.setSelection(
                            "ml".equals(food.get("label_unit"))
                                    ? 1 : 0
                    );

                    for (int i = 0; i < nutrientInputs.length; i++) {
                        Object value = ((Map<?, ?>) label).get(
                                FoodNutrition.KEYS[i]
                        );

                        nutrientInputs[i].setText(
                                value == null ? "" : value.toString()
                        );
                    }
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

    private void saveProduct() {
        if (saving) return;

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || !uid.equals(user.getUid())) {
            status.setText(R.string.fl_sign_in);
            return;
        }

        if (!confirmation.isChecked()) {
            status.setText(R.string.sb_missing_confirmation);
            return;
        }

        final Map<String, Object> food;

        try {
            Map<String, Object> label = new HashMap<>();

            for (int i = 0; i < nutrientInputs.length; i++) {
                String raw = nutrientInputs[i].getText()
                        .toString().trim();

                if (!raw.isEmpty()) {
                    label.put(
                            FoodNutrition.KEYS[i],
                            Double.parseDouble(
                                    raw.replace(',', '.')
                            )
                    );
                }
            }

            food = UserBarcodeFood.create(
                    barcode,
                    name.getText().toString(),
                    Double.parseDouble(
                            reference.getText().toString()
                                    .trim()
                                    .replace(',', '.')
                    ),
                    unit.getSelectedItem().toString(),
                    label
            );

        } catch (IllegalArgumentException invalid) {
            status.setText(R.string.sb_invalid);
            return;
        }

        food.put(
                "updated_at",
                FieldValue.serverTimestamp()
        );

        saving = true;
        controls(false);
        status.setText(R.string.fl_saving);

        SavedBarcodeFoods.reference(uid, barcode)
                .set(food)
                .addOnSuccessListener(unused -> {
                    if (isDestroyed() || isFinishing()) return;

                    Toast.makeText(
                            this,
                            R.string.sb_saved,
                            Toast.LENGTH_LONG
                    ).show();

                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(error -> {
                    if (isDestroyed() || isFinishing()) return;

                    saving = false;
                    controls(true);

                    status.setText(FoodUi.error(
                            this,
                            R.string.fl_save_operation,
                            error
                    ));
                });
    }
}