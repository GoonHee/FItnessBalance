package com.example.fitnessbalance;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class PreferencesActivity extends AppCompatActivity {
    private String uid;
    private LinearLayout form;
    private TextView status;
    private Button save, retry;
    private EditText minutes, target;
    private final Map<String, EditText> goals = new LinkedHashMap<>();
    private final List<CheckBox> days = new ArrayList<>(), equipment = new ArrayList<>(), diets = new ArrayList<>();
    private boolean loaded, saving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        ScrollView root = new ScrollView(this);
        root.setId(R.id.foodRoot);
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        root.addView(form);
        setContentView(root);
        FoodUi.insets(this);
        Button back = new Button(this); back.setText(R.string.back);
        back.setOnClickListener(v -> finish()); form.addView(back);
        heading(R.string.prefs_title);
        note(R.string.prefs_intro);
        heading(R.string.prefs_workout);
        minutes = number(R.string.prefs_minutes, false);
        target = number(R.string.prefs_target, false);
        note(R.string.prefs_days);
        for (String label : getResources().getStringArray(R.array.prefs_days)) days.add(check(label));
        note(R.string.prefs_equipment);
        for (String label : getResources().getStringArray(R.array.prefs_equipment)) equipment.add(check(label));
        for (int i = 0; i < equipment.size(); i++) {
            final int index = i;
            equipment.get(i).setOnCheckedChangeListener((button, checked) -> {
                if (!checked) return;
                if (index == 0) {
                    for (int j = 1; j < equipment.size(); j++) equipment.get(j).setChecked(false);
                } else equipment.get(0).setChecked(false);
            });
        }
        heading(R.string.prefs_nutrition);
        note(R.string.prefs_goal_note);
        int[] labels = {R.string.prefs_calories, R.string.prefs_protein, R.string.prefs_carbs, R.string.prefs_fat};
        for (int i = 0; i < labels.length; i++) goals.put(UserPreferences.GOALS[i], number(labels[i], true));
        heading(R.string.prefs_diet);
        note(R.string.prefs_diet_note);
        for (String label : getResources().getStringArray(R.array.prefs_diets)) diets.add(check(label));
        status = new TextView(this); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(status);
        retry = new Button(this); retry.setText(R.string.prefs_retry); retry.setOnClickListener(v -> load()); form.addView(retry);
        save = new Button(this); save.setText(R.string.prefs_save); save.setOnClickListener(v -> save()); form.addView(save);
        if (state != null && uid.equals(state.getString("uid")) && state.getBoolean("loaded")) {
            minutes.setText(state.getString("minutes")); target.setText(state.getString("target"));
            for (String key : goals.keySet()) goals.get(key).setText(state.getString(key));
            restore(days, state, "days"); restore(equipment, state, "equipment"); restore(diets, state, "diets");
            loaded = true; retry.setVisibility(View.GONE); enable(true);
        } else load();
    }

    private void heading(int text) { TextView view = new TextView(this); view.setText(text); view.setTextSize(22); form.addView(view); }
    private void note(int text) { TextView view = new TextView(this); view.setText(text); form.addView(view); }
    private EditText number(int label, boolean decimal) {
        TextView caption = new TextView(this); caption.setText(label); form.addView(caption);
        EditText input = new EditText(this); input.setId(View.generateViewId()); input.setSaveEnabled(false);
        caption.setLabelFor(input.getId()); input.setSingleLine(true); input.setMinHeight((int)(48 * getResources().getDisplayMetrics().density));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        form.addView(input); return input;
    }
    private CheckBox check(String text) { CheckBox c = new CheckBox(this); c.setText(text); c.setSaveEnabled(false); form.addView(c); return c; }
    private void enable(boolean value) {
        minutes.setEnabled(value); target.setEnabled(value);
        for (EditText input : goals.values()) input.setEnabled(value);
        for (List<CheckBox> group : Arrays.asList(days, equipment, diets)) for (CheckBox c : group) c.setEnabled(value);
        save.setEnabled(value && loaded);
    }
    private DocumentReference profile() { return FirebaseFirestore.getInstance().collection("users").document(uid); }
    private boolean sameUser() { FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); return user != null && uid.equals(user.getUid()); }
    private void load() {
        loaded = false; enable(false); retry.setVisibility(View.GONE); status.setText(R.string.fl_loading);
        if (!sameUser()) { status.setText(R.string.fl_sign_in); return; }
        profile().get(Source.SERVER).addOnSuccessListener(this, document -> {
            if (!sameUser()) { finish(); return; }
            if (!document.exists()) { status.setText(R.string.prefs_missing_profile); return; }
            minutes.setText(String.valueOf(com.example.fitnessbalance.util.Values.integer(document.get("preferredWorkoutMinutes"), 10)));
            target.setText(String.valueOf(com.example.fitnessbalance.util.Values.integer(document.get("weeklyWorkoutTarget"), 3)));
            Object selectedDays = document.get("preferredWorkoutDays");
            for (int i = 0; i < days.size(); i++) {
                boolean selected = false;
                if (selectedDays instanceof List) for (Object day : (List<?>) selectedDays)
                    if (day instanceof Number && ((Number) day).intValue() == i + 1) selected = true;
                days.get(i).setChecked(selected);
            }
            choose(equipment, UserPreferences.EQUIPMENT, document.get("equipment"));
            boolean any = false; for (CheckBox c : equipment) any |= c.isChecked();
            if (!any) equipment.get(0).setChecked(true);
            choose(diets, UserPreferences.DIETS, document.get("dietaryPreferences"));
            for (String key : goals.keySet()) {
                Object value = document.get(key);
                goals.get(key).setText(value instanceof Number ? value.toString() : "");
            }
            loaded = true; enable(true);
            status.setText(document.contains("dailyCalorieGoal") && !document.contains("nutritionGoalSource")
                    ? R.string.prefs_legacy_goal : R.string.prefs_loaded);
        }).addOnFailureListener(this, error -> { status.setText(R.string.prefs_load_error); retry.setVisibility(View.VISIBLE); });
    }
    private void choose(List<CheckBox> views, List<String> keys, Object raw) {
        for (int i = 0; i < views.size(); i++) views.get(i).setChecked(raw instanceof List && ((List<?>) raw).contains(keys.get(i)));
    }
    private List<String> chosen(List<CheckBox> views, List<String> keys) {
        List<String> result = new ArrayList<>(); for (int i = 0; i < views.size(); i++) if (views.get(i).isChecked()) result.add(keys.get(i)); return result;
    }
    private void save() {
        if (!loaded || saving) return;
        if (!sameUser()) { status.setText(R.string.fl_sign_in); return; }
        final Map<String, Object> values;
        try {
            Map<String, Double> nutrition = new HashMap<>();
            for (String key : goals.keySet()) {
                String text = goals.get(key).getText().toString().trim().replace(',', '.');
                if (!text.isEmpty()) nutrition.put(key, Double.parseDouble(text));
            }
            List<Integer> selectedDays = new ArrayList<>();
            for (int i = 0; i < days.size(); i++) if (days.get(i).isChecked()) selectedDays.add(i + 1);
            values = UserPreferences.validate(Integer.parseInt(minutes.getText().toString().trim()),
                    Integer.parseInt(target.getText().toString().trim()), selectedDays,
                    chosen(equipment, UserPreferences.EQUIPMENT), chosen(diets, UserPreferences.DIETS), nutrition);
            for (String key : UserPreferences.GOALS) if (!values.containsKey(key)) values.put(key, FieldValue.delete());
            values.put("nutritionGoalSource", nutrition.isEmpty() ? "not_set" : "user_entered");
            values.put("preferencesUpdatedAt", FieldValue.serverTimestamp());
        } catch (IllegalArgumentException invalid) { status.setText(R.string.prefs_invalid); return; }
        saving = true; enable(false); status.setText(R.string.fl_saving);
        FirebaseFirestore.getInstance().runTransaction(tx -> {
            if (!tx.get(profile()).exists()) throw new IllegalStateException("Missing profile");
            tx.set(profile(), values, SetOptions.merge()); return null;
        }).addOnSuccessListener(this, unused -> { saving = false; enable(true); status.setText(R.string.prefs_saved); })
          .addOnFailureListener(this, error -> { saving = false; enable(true); status.setText(R.string.prefs_save_error); });
    }
    @Override protected void onStart() { super.onStart(); if (uid != null && !sameUser()) finish(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (uid == null) return;
        state.putString("uid", uid); state.putBoolean("loaded", loaded);
        state.putString("minutes", minutes.getText().toString()); state.putString("target", target.getText().toString());
        for (String key : goals.keySet()) state.putString(key, goals.get(key).getText().toString());
        store(days, state, "days"); store(equipment, state, "equipment"); store(diets, state, "diets");
    }
    private void store(List<CheckBox> views, Bundle state, String key) { boolean[] selected = new boolean[views.size()]; for (int i = 0; i < selected.length; i++) selected[i] = views.get(i).isChecked(); state.putBooleanArray(key, selected); }
    private void restore(List<CheckBox> views, Bundle state, String key) { boolean[] selected = state.getBooleanArray(key); if (selected != null) for (int i = 0; i < Math.min(selected.length, views.size()); i++) views.get(i).setChecked(selected[i]); }
}
