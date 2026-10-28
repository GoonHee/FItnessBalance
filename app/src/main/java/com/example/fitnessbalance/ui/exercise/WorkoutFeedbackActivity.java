package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.repository.GamificationRepository;
import com.example.fitnessbalance.util.IntentKeys;

public class WorkoutFeedbackActivity extends ExerciseBaseActivity {
  private SessionDraft d;
  private boolean saving;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_feedback);
    findViewById(R.id.exSubmit).setOnClickListener(v -> submit());
    ((RadioGroup) findViewById(R.id.exFeeling))
        .setOnCheckedChangeListener(
            (g, id) ->
                findViewById(R.id.exSafety)
                    .setVisibility(id == R.id.ex_discomfort ? View.VISIBLE : View.GONE));
    ready = true;
  }

  @Override
  protected void load() {
    d = SessionDraft.read(this);
    if (d == null || !d.id.equals(getIntent().getStringExtra(IntentKeys.SESSION))) {
      error(new IllegalStateException());
      return;
    }
    loaded();
    findViewById(R.id.exSafety)
        .setVisibility(
            ((RadioGroup) findViewById(R.id.exFeeling)).getCheckedRadioButtonId()
                    == R.id.ex_discomfort
                ? View.VISIBLE
                : View.GONE);
  }

  private void submit() {
    if (saving || d == null) return;
    int a = ((RadioGroup) findViewById(R.id.exDifficulty)).getCheckedRadioButtonId(),
        b = ((RadioGroup) findViewById(R.id.exFeeling)).getCheckedRadioButtonId();
    if (a == -1 || b == -1) {
      message(R.string.ex_feedback_missing);
      return;
    }
    String difficulty =
        a == R.id.ex_too_easy ? "too_easy" : a == R.id.ex_suitable ? "suitable" : "too_difficult";
    String feeling =
        b == R.id.ex_energized
            ? "energized"
            : b == R.id.ex_normal ? "normal" : b == R.id.ex_tired ? "tired" : "discomfort";
    saving = true;
    findViewById(R.id.exSubmit).setEnabled(false);
    message(R.string.ex_save_pending);
    new GamificationRepository()
        .complete(
            d.id,
            d.completed,
            d.skipped,
            (int) (d.elapsedMs / 1000),
            (int) (d.exerciseMs / 1000),
            d.calories,
            difficulty,
            feeling)
        .addOnSuccessListener(
            this,
            id -> {
              SessionDraft.clear(this, id);
              Intent i = new Intent(this, WorkoutResultActivity.class);
              i.putExtra(IntentKeys.SESSION, id);
              startActivity(i);
              finish();
            })
        .addOnFailureListener(
            this,
            e -> {
              saving = false;
              findViewById(R.id.exSubmit).setEnabled(true);
              error(e);
            });
  }
}
