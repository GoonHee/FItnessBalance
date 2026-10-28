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
    private String activeSearch = "";

    private int searchVersion;
    private boolean searching;

    private DocumentSnapshot cursor;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_food_log_redesign);
        FoodUi.insets(this);

        selectedDate = state == null
                ? FoodUi.date(new Date())
                : state.getString("date");

        if (!FoodUi.validDate(selectedDate)) {
            selectedDate = FoodUi.date(new Date());
        }

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
                    if (!date.equals(selectedDate)
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