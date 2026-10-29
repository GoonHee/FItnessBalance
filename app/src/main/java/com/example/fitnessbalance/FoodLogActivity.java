package com.example.fitnessbalance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FoodLogActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 30;

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    private EditText searchInput;
    private TextView searchStatus, historyStatus, totals;
    private LinearLayout results, history;
    private Button dateButton, more, retry;

    private String selectedDate;
    private com.example.fitnessbalance.util.LocalDayObserver days;
    private int logVersion;
    private String activeSearch = "";

    private int searchVersion;
    private boolean searching;

    private DocumentSnapshot cursor;
    private ListenerRegistration registration;

    private EditText editNumber(LinearLayout form, String label, Object value) {
        TextView caption = new TextView(this);
        caption.setText(label);
        form.addView(caption);
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (value instanceof Number) input.setText(value.toString());
        form.addView(input);
        return input;
    }

    private void editEntry(String uid, String date, Map<String, Object> entry) {
        if (!(entry.get("entryId") instanceof String)
                || !(entry.get("totalAmount") instanceof Number)) {
            new AlertDialog.Builder(this).setMessage(R.string.fe_legacy)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        EditText amount = editNumber(form, getString(R.string.fs_serving_size)
                + " (" + entry.get("portionUnit") + ")", entry.get("servingAmount"));
        EditText count = editNumber(form, getString(R.string.fs_count), entry.get("servings"));
        boolean manual = "manual".equals(entry.get("calculationMode"));
        EditText calories = manual ? editNumber(form, getString(R.string.fs_manual_calories),
                entry.get("manualCaloriesPerServing")) : null;
        android.widget.Spinner meal = new android.widget.Spinner(this);
        String[] meals = getResources().getStringArray(R.array.fs_meals);
        meal.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, meals));
        for (int i = 0; i < meals.length; i++) {
            if (meals[i].equals(entry.get("meal"))) meal.setSelection(i);
        }
        TextView mealLabel = new TextView(this);
        mealLabel.setText(R.string.fs_meal);
        form.addView(mealLabel);
        form.addView(meal);
        TextView preview = new TextView(this);
        form.addView(preview);
        java.util.concurrent.Callable<Map<String, Object>> calculation = () ->
                FoodEntryEditor.resize(entry,
                        Double.parseDouble(amount.getText().toString()),
                        Double.parseDouble(count.getText().toString()),
                        meal.getSelectedItem().toString(), manual
                                ? Double.parseDouble(calories.getText().toString()) : 0);
        Runnable refresh = () -> {
            try {
                Map<String, Object> resized = calculation.call();
                preview.setText(FoodUi.nutrients(this,
                        (Map<?, ?>) resized.get("nutrients"), null, 1));
            } catch (Exception invalid) { preview.setText(R.string.fs_invalid); }
        };
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            public void afterTextChanged(android.text.Editable s) {}
        };
        amount.addTextChangedListener(watcher);
        count.addTextChangedListener(watcher);
        if (calories != null) calories.addTextChangedListener(watcher);
        refresh.run();
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.fe_edit).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.fe_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final Map<String, Object> replacement;
                    try { replacement = calculation.call(); }
                    catch (Exception invalid) { preview.setText(R.string.fs_invalid); return; }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                    dialog.setCancelable(false);
                    FoodLogRepository.update(uid, date, entry, replacement)
                            .addOnSuccessListener(unused -> dialog.dismiss())
                            .addOnFailureListener(error -> {
                                if (isDestroyed() || isFinishing()) return;
                                dialog.setCancelable(true);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                preview.setText(getString(R.string.fe_failed));
                            });
                }));
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_food_log_redesign);
        FoodUi.insets(this);

        String today = com.example.fitnessbalance.util.LocalDayObserver.today();
        selectedDate = com.example.fitnessbalance.util.LocalDayState.selection(
                state == null ? null : state.getString("date"),
                state == null ? null : state.getString("observedDay"), today);
        if (!FoodUi.validDate(selectedDate)) selectedDate = today;

        searchInput = findViewById(R.id.foodSearchInput);
        searchStatus = findViewById(R.id.foodSearchStatus);
        historyStatus = findViewById(R.id.foodHistoryStatus);
        results = findViewById(R.id.foodSearchResults);
        history = findViewById(R.id.foodHistory);
        totals = findViewById(R.id.foodDailyTotals);
        dateButton = findViewById(R.id.foodDate);
        more = findViewById(R.id.foodMore);
        retry = findViewById(R.id.foodRetry);

        findViewById(R.id.foodBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.foodSearchButton)
                .setOnClickListener(v -> search());

        searchInput.setOnEditorActionListener(
                (view, action, event) -> {
                    if (action == EditorInfo.IME_ACTION_SEARCH) {
                        search();
                        return true;
                    }
                    return false;
                }
        );

        Button barcodeButton = findViewById(R.id.foodBarcodeScan);

        barcodeButton.setOnClickListener(v ->
                FoodBarcodeScanner.scan(
                        this,
                        barcodeButton,
                        selectedDate
                )
        );

        findViewById(R.id.foodBarcodeEnter).setOnClickListener(v ->
                FoodBarcodeScanner.enter(this, selectedDate)
        );

        more.setOnClickListener(v -> loadSearchPage());
        retry.setOnClickListener(v -> observeLog());
        dateButton.setOnClickListener(v -> pickDate());

        dateButton.setText(
                getString(R.string.fl_date, selectedDate)
        );

        days = new com.example.fitnessbalance.util.LocalDayObserver(this, this, today, () -> {
            selectedDate = com.example.fitnessbalance.util.LocalDayObserver.today();
            dateButton.setText(getString(R.string.fl_date, selectedDate));
            observeLog();
        });

        if (state != null) {
            searchInput.setText(state.getString("query", ""));

            if (!searchInput.getText().toString().trim().isEmpty()) {
                search();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        observeLog();
    }

    @Override
    protected void onStop() {
        logVersion++;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        searchVersion++;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("date", selectedDate);
        state.putString("observedDay", days.day());
        state.putString(
                "query",
                searchInput.getText().toString()
        );
        super.onSaveInstanceState(state);
    }

    private void pickDate() {
        Calendar date = Calendar.getInstance();
        String[] parts = selectedDate.split("-");

        date.set(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]) - 1,
                Integer.parseInt(parts[2])
        );

        new DatePickerDialog(
                this,
                (picker, year, month, day) -> {
                    selectedDate = String.format(
                            Locale.ROOT,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            day
                    );

                    dateButton.setText(
                            getString(R.string.fl_date, selectedDate)
                    );

                    observeLog();
                },
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void search() {
        searchVersion++;
        searching = false;
        cursor = null;

        results.removeAllViews();
        more.setVisibility(View.GONE);

        activeSearch = searchInput.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (activeSearch.isEmpty()) {
            searchStatus.setText(R.string.fl_prefix_note);
            return;
        }

        loadSearchPage();
    }

    private void loadSearchPage() {
        if (searching) return;

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            searchStatus.setText(R.string.fl_sign_in);
            return;
        }

        searching = true;
        more.setEnabled(false);
        searchStatus.setText(R.string.fl_loading);

        final int version = searchVersion;

        Query query = db.collection("foods")
                .orderBy("food_name_lowercase")
                .startAt(activeSearch)
                .endAt(activeSearch + "\uf8ff")
                .limit(PAGE_SIZE);

        if (cursor != null) {
            query = query.startAfter(cursor);
        }

        query.get().addOnSuccessListener(snapshot -> {
            if (version != searchVersion
                    || isDestroyed()
                    || isFinishing()) {
                return;
            }

            searching = false;
            more.setEnabled(true);

            List<DocumentSnapshot> page =
                    snapshot.getDocuments();

            for (DocumentSnapshot food : page) {
                Button row = new Button(this);
                String name = food.getString("food_name");

                row.setText(name == null
                        ? getString(R.string.fl_name_missing)
                        : name);

                row.setAllCaps(false);

                row.setOnClickListener(v -> {
                    Intent intent = new Intent(
                            this,
                            FoodDetailsActivity.class
                    );

                    intent.putExtra("foodId", food.getId());
                    intent.putExtra("date", selectedDate);

                    startActivity(intent);
                });

                results.addView(row);
            }

            if (!page.isEmpty()) {
                cursor = page.get(page.size() - 1);
            }

            more.setVisibility(page.size() == PAGE_SIZE
                    ? View.VISIBLE : View.GONE);

            searchStatus.setText(results.getChildCount() == 0
                    ? R.string.fl_no_results
                    : R.string.fl_results);

        }).addOnFailureListener(error -> {
            if (version != searchVersion
                    || isDestroyed()
                    || isFinishing()) {
                return;
            }

            searching = false;
            more.setEnabled(true);

            searchStatus.setText(FoodUi.error(
                    this,
                    R.string.fl_search_operation,
                    error
            ));
        });
    }

    private void observeLog() {
        final int version = ++logVersion;
        if (registration != null) {
            registration.remove();
            registration = null;
        }

        history.removeAllViews();
        totals.setText("");
        retry.setVisibility(View.GONE);

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            historyStatus.setText(R.string.fl_sign_in);
            return;
        }

        historyStatus.setText(R.string.fl_loading);
        final String date = selectedDate;

        registration = db.collection("users")
                .document(user.getUid())
                .collection("foodLogs")
                .document(date)
                .addSnapshotListener((document, error) -> {
                    if (version != logVersion || !date.equals(selectedDate)
                            || isDestroyed()
                            || isFinishing()) {
                        return;
                    }

                    if (error != null) {
                        historyStatus.setText(FoodUi.error(
                                this,
                                R.string.fl_load_operation,
                                error
                        ));

                        retry.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (document == null) return;

                    try {
                        renderLog(
                                FoodLogRepository.entries(document),
                                user.getUid(),
                                date
                        );
                    } catch (RuntimeException invalid) {
                        historyStatus.setText(FoodUi.error(
                                this,
                                R.string.fl_load_operation,
                                invalid
                        ));

                        retry.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void renderLog(
            List<Map<String, Object>> entries,
            String uid,
            String date
    ) {
        history.removeAllViews();
        retry.setVisibility(View.GONE);

        historyStatus.setText(entries.isEmpty()
                ? getString(R.string.fl_empty) : "");

        Map<String, Object> summary =
                FoodNutrition.totals(entries);

        totals.setText(FoodUi.nutrients(
                this,
                (Map<?, ?>) summary.get("nutrientTotals"),
                (Map<?, ?>) summary.get("nutrientCoverage"),
                entries.size()
        ));

        for (Map<String, Object> entry : entries) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundResource(R.drawable.food_panel);

            int padding = (int) (
                    16 * getResources().getDisplayMetrics().density
            );

            panel.setPadding(
                    padding, padding, padding, padding
            );

            TextView title = new TextView(this);

            title.setText(entry.get("name") instanceof String
                    ? (String) entry.get("name")
                    : getString(R.string.fl_name_missing));

            title.setTextSize(18);
            panel.addView(title);

            TextView subtitle = new TextView(this);

            if (entry.get("totalAmount") instanceof Number) {
                String line = getString(
                        R.string.fs_history,
                        String.valueOf(entry.get("meal")),
                        FoodUi.number(entry.get("servings")),
                        FoodUi.number(entry.get("servingAmount")),
                        String.valueOf(entry.get("portionUnit")),
                        FoodUi.number(entry.get("calories"))
                );

                if ("manual".equals(entry.get("calculationMode"))) {
                    line += " · " + getString(R.string.fs_user_entered);
                }

                subtitle.setText(line);

            } else if (FoodNutrition.number(
                    entry.get("portionGrams")
            ) != null) {
                subtitle.setText(getString(
                        R.string.fl_entry_portion,
                        FoodUi.number(entry.get("portionGrams")),
                        FoodUi.number(entry.get("calories"))
                ));

            } else {
                subtitle.setText(getString(
                        R.string.fl_legacy_portion,
                        FoodUi.number(entry.get("calories"))
                ));
            }

            panel.addView(subtitle);

            Button edit = new Button(this);
            edit.setText(R.string.fe_edit);
            edit.setOnClickListener(v -> editEntry(uid, date, entry));
            panel.addView(edit);

            Button remove = new Button(this);
            remove.setText(R.string.fl_remove);

            remove.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.fl_remove_title)
                            .setMessage(title.getText())
                            .setNegativeButton(
                                    android.R.string.cancel,
                                    null
                            )
                            .setPositiveButton(
                                    R.string.fl_remove,
                                    (dialog, which) -> {
                                        remove.setEnabled(false);

                                        FoodLogRepository.save(
                                                uid,
                                                date,
                                                entry,
                                                true
                                        ).addOnFailureListener(error -> {
                                            if (isDestroyed()
                                                    || isFinishing()) {
                                                return;
                                            }

                                            remove.setEnabled(true);

                                            historyStatus.setText(
                                                    FoodUi.error(
                                                            this,
                                                            R.string.fl_save_operation,
                                                            error
                                                    )
                                            );
                                        });
                                    }
                            )
                            .show()
            );

            panel.addView(remove);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.bottomMargin = padding;
            history.addView(panel, params);
        }
    }
}