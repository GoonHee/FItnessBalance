package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutDraft;
import com.example.fitnessbalance.repository.GamificationRepository;
import com.example.fitnessbalance.util.IntentKeys;

public final class WorkoutFeedbackActivity extends ExerciseBaseActivity {
  private WorkoutDraft draft;
  private boolean saving;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_feedback);
    findViewById(R.id.exSave).setOnClickListener(v -> submit());
    ((RadioGroup) findViewById(R.id.exFeeling))
        .setOnCheckedChangeListener(
            (g, id) -> {
              findViewById(R.id.exDiscomfortGuidance)
                  .setVisibility(id == R.id.ex_discomfort ? View.VISIBLE : View.GONE);
              persistFeedback();
            });
    ((RadioGroup) findViewById(R.id.exDifficulty))
        .setOnCheckedChangeListener((g, id) -> persistFeedback());
  }

  @Override
  protected void load() {
    saving = false;
    draft = drafts.read(uid);
    String id = getIntent().getStringExtra(IntentKeys.SESSION);
    if (id == null) {
      status(R.string.ex_session_missing);
      return;
    }
    loading();
    repository
        .record(id)
        .addOnSuccessListener(
            this,
            record -> {
              if (record.exists()) {
                drafts.clear(uid, id);
                openSession(WorkoutResultActivity.class, id);
                finish();
                return;
              }
              if (draft == null || !draft.sessionId.equals(id)) {
                loaded();
                status(R.string.ex_session_missing);
                return;
              }
              if (!draft.difficulty.isEmpty())
                ((RadioGroup) findViewById(R.id.exDifficulty))
                    .check(
                        "too_easy".equals(draft.difficulty)
                            ? R.id.ex_too_easy
                            : "suitable".equals(draft.difficulty)
                                ? R.id.ex_suitable
                                : R.id.ex_too_difficult);
              if (!draft.feeling.isEmpty())
                ((RadioGroup) findViewById(R.id.exFeeling))
                    .check(
                        "energized".equals(draft.feeling)
                            ? R.id.ex_energized
                            : "normal".equals(draft.feeling)
                                ? R.id.ex_normal
                                : "tired".equals(draft.feeling)
                                    ? R.id.ex_tired
                                    : R.id.ex_discomfort);
              findViewById(R.id.exDiscomfortGuidance)
                  .setVisibility("discomfort".equals(draft.feeling) ? View.VISIBLE : View.GONE);
              enabled(R.id.exSave, true);
              loaded();
            })
        .addOnFailureListener(this, this::error);
  }

  private void persistFeedback() {
    if (draft == null) return;
    int a = ((RadioGroup) findViewById(R.id.exDifficulty)).getCheckedRadioButtonId(),
        b = ((RadioGroup) findViewById(R.id.exFeeling)).getCheckedRadioButtonId();
    if (a != -1)
      draft.difficulty =
          a == R.id.ex_too_easy ? "too_easy" : a == R.id.ex_suitable ? "suitable" : "too_difficult";
    if (b != -1)
      draft.feeling =
          b == R.id.ex_energized
              ? "energized"
              : b == R.id.ex_normal ? "normal" : b == R.id.ex_tired ? "tired" : "discomfort";
    drafts.save(draft);
  }

  private void submit() {
    if (saving || draft == null) return;
    persistFeedback();
    if (draft.difficulty.isEmpty() || draft.feeling.isEmpty()) {
      status(R.string.ex_missing_feedback);
      return;
    }
    saving = true;
    enabled(R.id.exSave, false);
    status(R.string.ex_save_busy);
    try {
      new GamificationRepository()
          .complete(
              draft.sessionId,
              draft.state.completed,
              draft.state.skipped,
              (int) (draft.state.elapsedMs / 1000),
              (int) (draft.state.activeMs / 1000),
              draft.calories,
              draft.difficulty,
              draft.feeling)
          .addOnSuccessListener(
              this,
              id -> {
                drafts.clear(uid, id);
                openSession(WorkoutResultActivity.class, id);
                finish();
              })
          .addOnFailureListener(
              this,
              e -> {
                saving = false;
                enabled(R.id.exSave, true);
                error(e);
                status(R.string.ex_save_error);
              });
    } catch (RuntimeException e) {
      saving = false;
      enabled(R.id.exSave, true);
      error(e);
    }
  }
}
