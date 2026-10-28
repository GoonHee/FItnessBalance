package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutRecord;
import com.example.fitnessbalance.service.GamificationService;
import com.example.fitnessbalance.util.IntentKeys;

public class WorkoutResultActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_result);
    findViewById(R.id.exContinue)
        .setOnClickListener(
            v -> {
              Intent i = new Intent(this, ExerciseHomeActivity.class);
              i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
              startActivity(i);
              finish();
            });
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    String id = getIntent().getStringExtra(IntentKeys.SESSION);
    if (id == null) {
      error(new IllegalArgumentException());
      return;
    }
    com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot>
        saved = repo.record(id),
        profile = repo.profile();
    com.google.android.gms.tasks.Tasks.whenAll(saved, profile)
        .addOnSuccessListener(
            this,
            s -> {
              WorkoutRecord r = saved.getResult().toObject(WorkoutRecord.class);
              com.google.firebase.firestore.DocumentSnapshot current = profile.getResult();
              int currentXp =
                  com.example.fitnessbalance.util.Values.integer(
                      current.get("totalXp"), r == null ? 0 : r.getTotalXpAfter());
              int target =
                  new com.example.fitnessbalance.service.MomentumService()
                      .target(
                          com.example.fitnessbalance.util.Values.integer(
                              current.get("weeklyWorkoutTarget"), 3));
              int weekly =
                  com.example.fitnessbalance.util.Values.integer(
                      com.example.fitnessbalance.util.Values.map(current.get("weeklyCounts"))
                          .get(
                              com.example.fitnessbalance.util.DateUtils.week(
                                  new java.util.Date(), java.util.TimeZone.getDefault())),
                      0);
              if (r == null) {
                error(new IllegalStateException());
                return;
              }
              GamificationService g = new GamificationService();
              text(
                  R.id.exSummary,
                  getString(
                      R.string.ex_result_summary,
                      getString(
                          r.getCompletionPercentage() >= 50
                              ? R.string.ex_finished
                              : R.string.ex_attempted),
                      r.getLessonName(),
                      r.getCompletionPercentage(),
                      r.getDurationSeconds(),
                      r.getEstimatedCaloriesBurned(),
                      r.getXpEarned(),
                      r.getStarsEarned(),
                      g.level(currentXp),
                      g.toNextLevel(currentXp),
                      weekly,
                      target));
              text(
                  R.id.exUnlocks,
                  r.getUnlocked().isEmpty()
                      ? getString(R.string.ex_no_unlocks)
                      : getString(R.string.ex_new_unlocks, unlockedNames(r.getUnlocked())));
              loaded();
            })
        .addOnFailureListener(this, this::error);
  }

  private String unlockedNames(java.util.List<String> ids) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (String id : ids) {
      int name;
      switch (id) {
        case "lesson_02":
          name = R.string.ex_lesson_02_name;
          break;
        case "lesson_03":
          name = R.string.ex_lesson_03_name;
          break;
        case "lesson_04":
          name = R.string.ex_lesson_04_name;
          break;
        case "lesson_05":
          name = R.string.ex_lesson_05_name;
          break;
        case "lesson_06":
          name = R.string.ex_lesson_06_name;
          break;
        case "lesson_07":
          name = R.string.ex_lesson_07_name;
          break;
        case "first_step":
          name = R.string.ex_first_step;
          break;
        case "routine_builder":
          name = R.string.ex_routine_builder;
          break;
        case "path_explorer":
          name = R.string.ex_path_explorer;
          break;
        case "welcome_back":
          name = R.string.ex_welcome_back;
          break;
        case "honest_reflection":
          name = R.string.ex_honest_reflection;
          break;
        case "balanced_week":
          name = R.string.ex_balanced_week;
          break;
        default:
          continue;
      }
      names.add(getString(name));
    }
    return String.join(", ", names);
  }
}
