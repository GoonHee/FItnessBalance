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
import com.google.android.gms.tasks.Task;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.Function;

final class DailyChestDialog {
  enum Quest {
    PLANNED("planned", R.string.ex_quest_planned),
    MINUTES("minutes", R.string.ex_quest_minutes),
    FEEDBACK("feedback", R.string.ex_quest_feedback);

    final String id;
    final int label;

    Quest(String id, int label) {
      this.id = id;
      this.label = label;
    }
  }

  private final ExerciseBaseActivity activity;
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
  private Function<Action, Task<Chest>> milestoneRequest;
  private String milestoneAccent;

  DailyChestDialog(ExerciseBaseActivity activity, String uid) {
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
    show(activity.getString(R.string.dc_title), null, null, null);
  }

  void showQuest(LocalDate day, Quest quest) {
    show(activity.getString(R.string.dc_quest_title), activity.getString(quest.label),
        DailyChestRepository.milestoneAccent(day + "_" + quest.id),
        action -> repository.quest(day, quest.id, action));
  }

  void showAchievement(String id, String label) {
    show(activity.getString(R.string.dc_achievement_title), label,
        DailyChestRepository.milestoneAccent(id), action -> repository.achievement(id, action));
  }

  private void show(String title, String label, String accent, Function<Action, Task<Chest>> request) {
    if (!currentAccount() || (dialog != null && dialog.isShowing())) return;
    date = LocalDate.now();
    milestoneRequest = request;
    milestoneAccent = accent;
    ScrollView scroll = new ScrollView(activity);
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.VERTICAL);
    int padding = dp(24);
    body.setPadding(padding, 0, padding, padding);
    scroll.addView(body);
    dateButton = button(body, R.string.dc_pick_date, this::pickDate);
    if (request != null) {
      dateButton.setVisibility(View.GONE);
      TextView milestone = text(body);
      milestone.setText(label);
      milestone.setGravity(Gravity.CENTER);
    }
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
    if (request == null) button(body, R.string.dc_quest_chests, this::chooseQuest);

    dialog = new AlertDialog.Builder(activity).setTitle(title)
        .setView(scroll).setPositiveButton(android.R.string.ok, null).create();
    dialog.setOnDismissListener(d -> {
      if (dialog == d) stopEffects();
    });
    dialog.show();
    load(Action.CHECK);
  }

  private void chooseQuest() {
    LocalDate selected = date;
    Quest[] quests = Quest.values();
    String[] choices = new String[quests.length];
    for (int i = 0; i < quests.length; i++) choices[i] = activity.getString(quests[i].label);
    secondaryDialog = new AlertDialog.Builder(activity).setTitle(R.string.dc_quest_chests)
        .setItems(choices, (picker, index) -> {
          dialog.dismiss();
          showQuest(selected, quests[index]);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
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
    boolean daily = milestoneRequest == null;
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
    String accent = daily ? DailyChestRepository.accent(date) : milestoneAccent;
    showPreview(accent);
    if (action != Action.OPEN) {
      illustration.show(ChestIllustrationView.State.LOCKED, accentColor(accent), false);
    }
    Task<Chest> request = daily ? repository.update(date, action) : milestoneRequest.apply(action);
    request.addOnSuccessListener(activity, chest -> {
      if (!currentAccount() || dialog != active || !active.isShowing() || !requested.equals(date)) return;
      dateButton.setEnabled(true);
      if (daily && requested.equals(LocalDate.now())) {
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
      if (celebrate) playChime();
      if (daily && !chest.opened) {
        goals.setText(activity.getString(R.string.dc_goals,
            state(chest.movement), state(chest.meal), state(chest.reflection)));
        rest.setVisibility(chest.restAvailable ? View.VISIBLE : View.GONE);
        reflect.setVisibility(requested.equals(LocalDate.now()) && !chest.reflection ? View.VISIBLE : View.GONE);
      }
      if (chest.opened) status.setText(R.string.dc_opened);
      else if (daily) status.setText(chest.earned ? R.string.dc_earned : R.string.dc_locked);
      else status.setText(chest.earned ? R.string.dc_milestone_ready : R.string.dc_milestone_locked);
      open.setVisibility(chest.earned && !chest.opened ? View.VISIBLE : View.GONE);
      apply.setVisibility(chest.opened ? View.VISIBLE : View.GONE);
    }).addOnFailureListener(activity, error -> {
      if (!currentAccount() || dialog != active || !active.isShowing()) return;
      dateButton.setEnabled(true);
      status.setText(daily ? R.string.dc_error : R.string.dc_milestone_error);
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
    if (ring != null) ring.setIndicatorColor(accentColor(accent));
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
