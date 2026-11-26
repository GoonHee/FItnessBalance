package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.service.AchievementProgress;
import static com.example.fitnessbalance.service.AchievementProgress.Metric.*;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class AchievementsActivity extends ExerciseBaseActivity {
  private DailyChestDialog chests;

  private static final int START = R.string.badge_start, CONSISTENCY = R.string.gm_consistency,
      EXPLORE = R.string.badge_explore, EFFORT = R.string.badge_effort, REFLECT = R.string.badge_reflect,
      RECOVERY = R.string.badge_recovery, COMEBACK = R.string.gm_comeback, NUTRITION = R.string.badge_nutrition;
  private static final int[] GROUPS = {START, CONSISTENCY, EXPLORE, EFFORT, REFLECT,
      RECOVERY, COMEBACK, NUTRITION};

  private static final class Milestone {
    final int title, category, detail, target;
    final AchievementProgress.Metric metric;

    Milestone(int title, int category, int detail, AchievementProgress.Metric metric, int target) {
      this.title = title;
      this.category = category;
      this.detail = detail;
      this.metric = metric;
      this.target = target;
    }
  }

  private static final Milestone[] MILESTONES = {
      new Milestone(R.string.badge_getting_started, START, R.string.badge_sessions, SESSIONS, 3),
      new Milestone(R.string.badge_first_week, CONSISTENCY, R.string.badge_active_days, ACTIVE_DAYS, 3),
      new Milestone(R.string.badge_weekly_target, CONSISTENCY, R.string.badge_weeks, TARGET_WEEKS, 1),
      new Milestone(R.string.badge_three_categories, EXPLORE, R.string.badge_categories, WORKOUT_CATEGORIES, 3),
      new Milestone(R.string.badge_variety_seeker, EXPLORE, R.string.badge_exercises, EXERCISES, 5),
      new Milestone(R.string.badge_all_rounder, EXPLORE, R.string.badge_rounder_weeks, ALL_ROUNDER, 1),
      new Milestone(R.string.badge_ten_sessions, EFFORT, R.string.badge_sessions, SESSIONS, 10),
      new Milestone(R.string.badge_path_finisher, EFFORT, R.string.badge_lessons, PATH_LESSONS, 7),
      new Milestone(R.string.badge_recovery_matters, RECOVERY, R.string.badge_recoveries, AchievementProgress.Metric.RECOVERY, 1),
      new Milestone(R.string.badge_back_on_track, COMEBACK, R.string.badge_return_sessions, COMEBACK_SESSIONS, 2),
      new Milestone(R.string.badge_food_starter, NUTRITION, R.string.badge_food_days, FOOD_DAYS, 3),
      new Milestone(R.string.badge_balanced_day, NUTRITION, R.string.badge_balance_days, BALANCED_DAYS, 1)
  };

  private static int category(String id) {
    switch (id) {
      case "first_step": return START;
      case "routine_builder": return CONSISTENCY;
      case "path_explorer": return EXPLORE;
      case "honest_reflection": return REFLECT;
      case "balanced_week": return NUTRITION;
      case "welcome_back": return COMEBACK;
      default: return R.string.gm_other_badges;
    }
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_achievements);
  }

  @Override
  protected void onStop() {
    if (chests != null) chests.onHostStop();
    super.onStop();
  }

  @Override
  protected void load() {
    loading();
    if (chests == null) chests = new DailyChestDialog(this, uid);
    Task<QuerySnapshot> definitions = repository.achievements(),
        unlocked = repository.unlockedAchievements();
    Task<Map<AchievementProgress.Metric, Integer>> progress = repository.achievementProgress();
    Tasks.whenAllComplete(definitions, unlocked, progress).addOnSuccessListener(this, tasks -> {
      if (!definitions.isSuccessful() || !unlocked.isSuccessful()) {
        error(!definitions.isSuccessful() ? definitions.getException() : unlocked.getException());
        return;
      }
      Set<String> ids = new HashSet<>();
      for (DocumentSnapshot badge : unlocked.getResult()) ids.add(badge.getId());
      Map<Integer, List<ExerciseRowsAdapter.Row>> groups = new LinkedHashMap<>();
      for (int group : GROUPS) groups.put(group, new ArrayList<>());
      for (DocumentSnapshot badge : definitions.getResult()) {
        String id = badge.getId(), name = Values.text(badge.get("name"), "");
        boolean earned = ids.contains(id);
        String state = getString(earned ? R.string.ex_earned : R.string.ex_not_earned);
        if (earned) state = getString(R.string.dc_chest_row, state);
        int group = category(id);
        groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(new ExerciseRowsAdapter.Row(
            name, Values.text(badge.get("description"), ""), state,
            earned ? () -> chests.showAchievement(id, name) : null));
      }
      if (progress.isSuccessful()) {
        Map<AchievementProgress.Metric, Integer> values = progress.getResult();
        for (Milestone badge : MILESTONES)
          groups.get(badge.category).add(row(getString(badge.title), badge.detail,
              values.getOrDefault(badge.metric, 0), badge.target));
        tiers(groups.get(EFFORT), R.string.badge_workout_tier, R.string.badge_sessions,
            values.getOrDefault(SESSIONS, 0), SESSIONS.tierTargets());
        tiers(groups.get(EFFORT), R.string.badge_active_tier, R.string.badge_minutes,
            values.getOrDefault(ACTIVE_MINUTES, 0), ACTIVE_MINUTES.tierTargets());
        tiers(groups.get(CONSISTENCY), R.string.badge_consistency_tier, R.string.badge_weeks,
            values.getOrDefault(TARGET_WEEKS, 0), TARGET_WEEKS.tierTargets());
        tiers(groups.get(EXPLORE), R.string.badge_variety_tier, R.string.badge_exercises,
            values.getOrDefault(EXERCISES, 0), EXERCISES.tierTargets());
        tiers(groups.get(REFLECT), R.string.badge_feedback_tier, R.string.badge_feedback,
            values.getOrDefault(FEEDBACK, 0), FEEDBACK.tierTargets());
      }
      List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
      for (Map.Entry<Integer, List<ExerciseRowsAdapter.Row>> group : groups.entrySet()) {
        if (group.getValue().isEmpty()) continue;
        rows.add(ExerciseRowsAdapter.Row.heading(getString(group.getKey())));
        rows.addAll(group.getValue());
      }
      list(R.id.exAchievementsList, new AchievementAdapter(rows));
      loaded();
      if (!progress.isSuccessful()) status(R.string.badge_progress_unavailable);
      else if (rows.isEmpty()) status(R.string.ex_no_achievements);
    });
  }

  private ExerciseRowsAdapter.Row row(String title, int detail, int value, int target) {
    return new ExerciseRowsAdapter.Row(title, getString(detail, Math.min(value, target), target),
        getString(AchievementProgress.earned(value, target) ? R.string.ex_earned : R.string.ex_not_earned), null);
  }

  private void tiers(List<ExerciseRowsAdapter.Row> rows, int title, int detail, int value, int... targets) {
    int[] labels = {R.string.badge_bronze, R.string.badge_silver, R.string.badge_gold};
    for (int i = 0; i < labels.length; i++)
      rows.add(row(getString(title, getString(labels[i])), detail, value, targets[i]));
  }
}
