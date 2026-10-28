package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutRecord;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;

public final class WorkoutResultActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_result);
    findViewById(R.id.exContinue)
        .setOnClickListener(
            v -> {
              startActivity(
                  new Intent(this, ExerciseHomeActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
              finish();
            });
  }

  @Override
  protected void load() {
    loading();
    String id = getIntent().getStringExtra(IntentKeys.SESSION);
    if (id == null) {
      error(new IllegalStateException());
      return;
    }
    Task<DocumentSnapshot> saved = repository.record(id),
        profile = repository.profile(),
        week = repository.week(DateUtils.week(new Date(), TimeZone.getDefault()));
    Tasks.whenAll(saved, profile, week)
        .addOnSuccessListener(
            this,
            v -> {
              WorkoutRecord r = saved.getResult().toObject(WorkoutRecord.class);
              if (r == null) {
                error(new IllegalStateException());
                return;
              }
              text(
                  R.id.exResultTitle,
                  getString(
                      r.getCompletionPercentage() >= 50
                          ? R.string.ex_finished
                          : R.string.ex_attempted));
              text(R.id.exLessonName, r.getLessonName());
              text(
                  R.id.exResultStats,
                  getString(
                      R.string.ex_result_stats,
                      r.getCompletionPercentage(),
                      r.getCompletedExercises(),
                      r.getPlannedExercises(),
                      r.getDurationSeconds(),
                      r.getActiveDurationSeconds(),
                      r.getEstimatedCaloriesBurned(),
                      r.getXpEarned(),
                      r.getStarsEarned()));
              text(
                  R.id.exUnlocks,
                  r.getUnlocked().isEmpty()
                      ? getString(R.string.ex_no_unlocks)
                      : getString(R.string.ex_unlocks, unlockedNames(r.getUnlocked())));
              progress(profile.getResult(), week.getResult());
              loaded();
            })
        .addOnFailureListener(this, this::error);
  }
}
