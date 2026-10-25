package com.example.fitnessbalance;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class FoodSearchActivity extends AppCompatActivity {
    private static final int PAGE_SIZE = 30;

    private final List<DocumentSnapshot> foods = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    private FirebaseFirestore firestore;
    private ArrayAdapter<String> adapter;
    private EditText searchInput;
    private TextView status;
    private Button moreButton;

    private DocumentSnapshot lastDocument;
    private String activeSearch = "";
    private int requestVersion = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_search);

        firestore = FirebaseFirestore.getInstance();
        searchInput = findViewById(R.id.searchInput);
        status = findViewById(R.id.searchStatus);
        moreButton = findViewById(R.id.loadMoreButton);
        ListView results = findViewById(R.id.searchResults);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                names
        );
        results.setAdapter(adapter);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.searchButton).setOnClickListener(v -> search());
        moreButton.setOnClickListener(v -> loadPage(false));

        results.setOnItemClickListener((parent, view, position, id) ->
                showFood(foods.get(position)));

        status.setText(R.string.food_search_prompt);
    }

    private void search() {
        // Invalidate callbacks from any previous search.
        requestVersion++;
        loading = false;
        lastDocument = null;
        foods.clear();
        names.clear();
        adapter.notifyDataSetChanged();
        moreButton.setVisibility(View.GONE);

        activeSearch = searchInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);

        if (activeSearch.isEmpty()) {
            status.setText(R.string.food_search_prompt);
            return;
        }

        loadPage(true);
    }

    private void loadPage(boolean firstPage) {
        if (loading) return;

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            status.setText(R.string.food_search_sign_in);
            return;
        }

        loading = true;
        moreButton.setEnabled(false);
        status.setText(R.string.food_search_loading);
        final int version = requestVersion;

        Query query = firestore.collection("foods")
                .orderBy("food_name_lowercase")
                .startAt(activeSearch)
                .endAt(activeSearch + "\uf8ff")
                .limit(PAGE_SIZE);

        if (!firstPage && lastDocument != null) {
            query = query.startAfter(lastDocument);
        }

        query.get()
                .addOnSuccessListener(snapshot -> {
                    if (!isCurrentRequest(version)) return;

                    loading = false;
                    moreButton.setEnabled(true);

                    List<DocumentSnapshot> page = snapshot.getDocuments();
                    for (DocumentSnapshot food : page) {
                        foods.add(food);
                        String name = food.getString("food_name");
                        names.add(name == null
                                ? getString(R.string.food_search_unnamed)
                                : name);
                    }

                    if (!page.isEmpty()) {
                        lastDocument = page.get(page.size() - 1);
                    }

                    adapter.notifyDataSetChanged();
                    moreButton.setVisibility(
                            page.size() == PAGE_SIZE
                                    ? View.VISIBLE : View.GONE
                    );

                    status.setText(foods.isEmpty()
                            ? getString(R.string.food_search_empty)
                            : getString(R.string.food_search_count, foods.size()));
                })
                .addOnFailureListener(error -> {
                    if (!isCurrentRequest(version)) return;

                    loading = false;
                    moreButton.setEnabled(true);

                    Log.e("FoodSearch", "Firestore search failed", error);

                    if (error instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreError =
                                (FirebaseFirestoreException) error;

                        status.setText("Search failed: " + firestoreError.getCode());
                    } else {
                        status.setText("Search failed. Check Logcat.");
                    }
                });
    }

    private boolean isCurrentRequest(int version) {
        return version == requestVersion
                && !isFinishing()
                && !isDestroyed();
    }

    private void showFood(DocumentSnapshot food) {
        String name = food.getString("food_name");
        String basis = food.getString("nutrition_basis");

        String basisText = basis == null
                || basis.trim().isEmpty()
                || "unknown".equalsIgnoreCase(basis.trim())
                ? getString(R.string.food_search_basis_unknown)
                : getString(R.string.food_search_basis, basis);

        String message = basisText + "\n\n"
                + nutrient(food, R.string.food_search_energy, "energy_kcal")
                + "\n" + nutrient(food, R.string.food_search_protein, "protein_g")
                + "\n" + nutrient(food, R.string.food_search_carbs, "carbohydrate_g")
                + "\n" + nutrient(food, R.string.food_search_fat, "fat_g")
                + "\n" + nutrient(food, R.string.food_search_fiber, "fiber_g")
                + "\n" + nutrient(food, R.string.food_search_sodium, "sodium_mg");

        new AlertDialog.Builder(this)
                .setTitle(name == null
                        ? getString(R.string.food_search_unnamed) : name)
                .setMessage(message)
                .setPositiveButton(R.string.food_search_close, null)
                .show();
    }

    private String nutrient(DocumentSnapshot food, int label, String field) {
        Object value = food.get("nutrition." + field);
        String display = getString(R.string.food_search_missing);

        if (value instanceof Number) {
            NumberFormat format = NumberFormat.getNumberInstance();
            format.setMaximumFractionDigits(2);
            display = format.format(value);
        }

        return getString(label, display);
    }

    @Override
    protected void onDestroy() {
        requestVersion++;
        super.onDestroy();
    }
}