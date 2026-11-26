package com.example.fitnessbalance.ui.exercise;

import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.view.Gravity;
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
  private Dialog secondaryDialog;
  private Button dateButton, rest, reflect, open, apply;
  private TextView goals, status, reward;
  private CircularProgressIndicator preview;
  private ChestIllustrationView illustration;
  private MediaPlayer chime;
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
    int padding = dp(24);
    body.setPadding(padding, 0, padding, padding);
    scroll.addView(body);
    dateButton = button(body, R.string.dc_pick_date, this::pickDate);
    illustration = new ChestIllustrationView(activity);
    body.addView(illustration, new LinearLayout.LayoutParams(-1, dp(136)));
    goals = text(body);
    rest = button(body, R.string.dc_check_rest, () -> load(Action.REST));
    reflect = button(body, R.string.dc_reflect, this::confirmReflection);
    LinearLayout rewardRow = new LinearLayout(activity);
    rewardRow.setGravity(Gravity.CENTER_VERTICAL);
    rewardRow.setPadding(dp(12), dp(8), dp(12), dp(8));
    rewardRow.setBackgroundResource(R.drawable.reward_panel_gold);
    body.addView(rewardRow, new LinearLayout.LayoutParams(-1, -2));
    preview = new CircularProgressIndicator(activity);
    preview.setIndicatorSize(dp(40));
    preview.setTrackThickness(dp(5));
    preview.setIndeterminate(false);
    preview.setProgress(100);
    preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    rewardRow.addView(preview, new LinearLayout.LayoutParams(dp(48), dp(48)));
    reward = new TextView(activity);
    reward.setTextSize(16);
    reward.setTextColor(ContextCompat.getColor(activity, R.color.gm_ink));
    reward.setPaddingRelative(dp(8), 0, 0, 0);
    rewardRow.addView(reward, new LinearLayout.LayoutParams(0, -2, 1));
    status = text(body);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    open = button(body, R.string.dc_open, () -> load(Action.OPEN));
    apply = button(body, R.string.dc_apply, () -> {
      if (!currentAccount() || unlockedAccent == null) return;
      preferences.edit().putString(uid, unlockedAccent).apply();
      applyAccent(unlockedAccent);
      status.setText(R.string.dc_applied);
    });
    CheckBox sound = new CheckBox(activity);
    sound.setText(R.string.dc_sound);
    sound.setMinHeight(dp(48));
    sound.setChecked(preferences.getBoolean(soundPreference(), false));
    sound.setOnCheckedChangeListener((view, checked) -> {
      preferences.edit().putBoolean(soundPreference(), checked).apply();
      if (!checked) stopChime();
    });
    body.addView(sound, new LinearLayout.LayoutParams(-1, -2));

    dialog = new AlertDialog.Builder(activity).setTitle(R.string.dc_title)
        .setView(scroll).setPositiveButton(android.R.string.ok, null).create();
    dialog.setOnDismissListener(d -> stopEffects());
    dialog.show();
    load(Action.CHECK);
  }

  private void confirmReflection() {
    secondaryDialog = new AlertDialog.Builder(activity).setTitle(R.string.dc_reflect)
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
    secondaryDialog = picker;
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
    stopEffects();
    String accent = DailyChestRepository.accent(date);
    showPreview(accent);
    if (action != Action.OPEN) {
      illustration.show(ChestIllustrationView.State.LOCKED, accentColor(accent), false);
    }
    repository.update(date, action).addOnSuccessListener(activity, chest -> {
      if (!currentAccount() || dialog != active || !active.isShowing() || !requested.equals(date)) return;
      dateButton.setEnabled(true);
      if (requested.equals(LocalDate.now())) {
        homeRefreshVersion++;
        showHomeState(chest);
      }
      boolean celebrate = action == Action.OPEN && chest.newlyOpened;
      boolean animate = celebrate && ValueAnimator.areAnimatorsEnabled();
      ChestIllustrationView.State art = chest.opened ? ChestIllustrationView.State.OPENED
          : chest.earned ? ChestIllustrationView.State.READY : ChestIllustrationView.State.LOCKED;
      illustration.show(art, accentColor(chest.accent), animate);
      showPreview(chest.accent);
      if (chest.opened) {
        unlockedAccent = chest.accent;
        reward.setText(activity.getString(R.string.dc_reward_unlocked, activity.getString(label(chest.accent))));
      }
      if (animate) {
        preview.setAlpha(0f);
        preview.animate().alpha(1f).setStartDelay(180).setDuration(240).start();
      }
      if (celebrate && preferences.getBoolean(soundPreference(), false)) playChime();
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
    preview.setIndicatorColor(accentColor(accent));
    reward.setText(activity.getString(R.string.dc_reward, activity.getString(label(accent))));
  }

  void onHostStop() {
    if (secondaryDialog != null) {
      secondaryDialog.dismiss();
      secondaryDialog = null;
    }
    if (dialog != null && dialog.isShowing()) dialog.dismiss();
    stopEffects();
  }

  private void stopEffects() {
    if (illustration != null) illustration.stopAnimation();
    if (preview != null) {
      preview.animate().cancel();
      preview.setAlpha(1f);
    }
    stopChime();
  }

  private void playChime() {
    stopChime();
    AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();
    try {
      chime = MediaPlayer.create(activity, R.raw.daily_chest_chime, attributes, 0);
      if (chime == null) return;
      chime.setOnCompletionListener(player -> {
        if (chime == player) stopChime();
      });
      chime.setOnErrorListener((player, what, extra) -> {
        if (chime == player) stopChime();
        return true;
      });
      chime.start();
    } catch (RuntimeException unavailable) {
      stopChime();
    }
  }

  private void stopChime() {
    if (chime == null) return;
    chime.release();
    chime = null;
  }

  private String soundPreference() {
    return uid + ".chest_sound";
  }

  private int dp(int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private int accentColor(String accent) {
    return ContextCompat.getColor(activity, color(accent));
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
    ring.setIndicatorColor(accentColor(accent));
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
    view.setPadding(0, dp(5), 0, dp(5));
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
