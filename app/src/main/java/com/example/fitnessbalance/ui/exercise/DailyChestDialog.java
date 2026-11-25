package com.example.fitnessbalance.ui.exercise;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.repository.DailyChestRepository;
import com.example.fitnessbalance.repository.DailyChestRepository.Action;
import com.example.fitnessbalance.repository.DailyChestRepository.Chest;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

final class DailyChestDialog {
  private final ExerciseHomeActivity activity;
  private final String uid;
  private final DailyChestRepository repository;
  private final SharedPreferences preferences;
  private LocalDate date = LocalDate.now();
  private AlertDialog dialog;
  private Button dateButton, rest, reflect, open, apply;
  private TextView goals, status, reward;
  private CircularProgressIndicator preview;
  private String unlockedAccent;
  private int homeRefreshVersion;

  DailyChestDialog(ExerciseHomeActivity activity, String uid) {
    this.activity = activity;
    this.uid = uid;
    repository = new DailyChestRepository(uid);
    preferences = activity.getSharedPreferences("daily_chest_accents", Context.MODE_PRIVATE);
  }

  void refreshToday() {
    if (!currentAccount()) return;
    applyAccent(preferences.getString(uid, ""));
    LocalDate today = LocalDate.now();
    int version = ++homeRefreshVersion;
    repository.update(today, Action.CHECK).addOnSuccessListener(activity, chest -> {
      if (version == homeRefreshVersion && currentAccount() && today.equals(LocalDate.now())) showHomeState(chest);
    });
  }

  void show() {
    if (!currentAccount() || (dialog != null && dialog.isShowing())) return;
    date = LocalDate.now();
    ScrollView scroll = new ScrollView(activity);
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.VERTICAL);
    int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
    body.setPadding(padding, 0, padding, padding);
    scroll.addView(body);
    dateButton = button(body, R.string.dc_pick_date, this::pickDate);
    goals = text(body);
    rest = button(body, R.string.dc_check_rest, () -> load(Action.REST));
    reflect = button(body, R.string.dc_reflect, this::confirmReflection);
    reward = text(body);
    preview = new CircularProgressIndicator(activity);
    preview.setIndeterminate(false);
    preview.setProgress(100);
    preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    body.addView(preview, new LinearLayout.LayoutParams(-1, -2));
    status = text(body);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    open = button(body, R.string.dc_open, () -> load(Action.OPEN));
    apply = button(body, R.string.dc_apply, () -> {
      if (!currentAccount() || unlockedAccent == null) return;
      preferences.edit().putString(uid, unlockedAccent).apply();
      applyAccent(unlockedAccent);
      status.setText(R.string.dc_applied);
    });
    dialog = new AlertDialog.Builder(activity).setTitle(R.string.dc_title)
        .setView(scroll).setPositiveButton(android.R.string.ok, null).show();
    load(Action.CHECK);
  }

  private void confirmReflection() {
    new AlertDialog.Builder(activity).setTitle(R.string.dc_reflect)
        .setMessage(R.string.dc_reflection_prompt)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.dc_done, (d, w) -> load(Action.REFLECT)).show();
  }

  private void pickDate() {
    DatePickerDialog picker = new DatePickerDialog(activity, (view, year, month, day) -> {
      date = LocalDate.of(year, month + 1, day);
      load(Action.CHECK);
    }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
    picker.show();
  }

  private void load(Action action) {
    if (!currentAccount() || date.isAfter(LocalDate.now())) return;
    AlertDialog active = dialog;
    LocalDate requested = date;
    dateButton.setEnabled(false);
    dateButton.setText(activity.getString(R.string.dc_date,
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))));
    goals.setText("");
    status.setText(R.string.ex_loading);
    rest.setVisibility(View.GONE);
    reflect.setVisibility(View.GONE);
    apply.setVisibility(View.GONE);
    open.setVisibility(View.GONE);
    unlockedAccent = null;
    showPreview(DailyChestRepository.accent(date));
    repository.update(date, action).addOnSuccessListener(activity, chest -> {
      if (!currentAccount() || dialog != active || !active.isShowing() || !requested.equals(date)) return;
      dateButton.setEnabled(true);
      if (requested.equals(LocalDate.now())) {
        homeRefreshVersion++;
        showHomeState(chest);
      }
      showPreview(chest.accent);
      if (chest.opened) unlockedAccent = chest.accent;
      if (!chest.opened) {
        goals.setText(activity.getString(R.string.dc_goals,
            state(chest.movement), state(chest.meal), state(chest.reflection)));
        rest.setVisibility(chest.restAvailable ? View.VISIBLE : View.GONE);
        reflect.setVisibility(requested.equals(LocalDate.now()) && !chest.reflection ? View.VISIBLE : View.GONE);
      }
      status.setText(chest.opened ? R.string.dc_opened : chest.earned ? R.string.dc_earned : R.string.dc_locked);
      open.setVisibility(chest.earned && !chest.opened ? View.VISIBLE : View.GONE);
      apply.setVisibility(chest.opened ? View.VISIBLE : View.GONE);
    }).addOnFailureListener(activity, error -> {
      if (!currentAccount() || dialog != active || !active.isShowing()) return;
      dateButton.setEnabled(true);
      status.setText(R.string.dc_error);
    });
  }

  private void showPreview(String accent) {
    preview.setIndicatorColor(ContextCompat.getColor(activity, color(accent)));
    reward.setText(activity.getString(R.string.dc_reward, activity.getString(label(accent))));
  }

  private void showHomeState(Chest chest) {
    activity.text(R.id.exDailyChest, activity.getString(chest.earned && !chest.opened
        ? R.string.dc_ready : R.string.dc_title));
  }

  private String state(boolean complete) {
    return activity.getString(complete ? R.string.ex_done : R.string.ex_pending);
  }

  private boolean currentAccount() {
    com.google.firebase.auth.FirebaseUser account = FirebaseAuth.getInstance().getCurrentUser();
    return account != null && uid.equals(account.getUid()) && !activity.isFinishing() && !activity.isDestroyed();
  }

  private void applyAccent(String accent) {
    CircularProgressIndicator ring = activity.findViewById(R.id.exWeeklyRing);
    ring.setIndicatorColor(ContextCompat.getColor(activity, color(accent)));
  }

  private static int color(String accent) {
    switch (accent) {
      case "amber": return R.color.dc_amber;
      case "purple": return R.color.gm_purple;
      default: return R.color.gm_teal;
    }
  }

  private static int label(String accent) {
    switch (accent) {
      case "amber": return R.string.dc_amber;
      case "purple": return R.string.dc_purple;
      default: return R.string.dc_teal;
    }
  }

  private TextView text(LinearLayout body) {
    TextView view = new TextView(activity);
    view.setTextSize(16);
    body.addView(view, new LinearLayout.LayoutParams(-1, -2));
    return view;
  }

  private Button button(LinearLayout body, int label, Runnable click) {
    Button button = new Button(activity);
    button.setText(label);
    button.setAllCaps(false);
    button.setOnClickListener(view -> click.run());
    body.addView(button, new LinearLayout.LayoutParams(-1, -2));
    return button;
  }
}
