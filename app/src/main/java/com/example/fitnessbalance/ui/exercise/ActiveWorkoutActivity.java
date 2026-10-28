package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.repository.ExerciseRepository;
import com.example.fitnessbalance.util.IntentKeys;
import com.google.android.gms.tasks.*;
import java.util.*;

public class ActiveWorkoutActivity extends ExerciseBaseActivity {
  private SessionDraft d;
  private List<WorkoutExercise> items;
  private final List<Exercise> exercises = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private long last, lastAction;
  private boolean visible, loaded, transitioning;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (loaded && visible && d != null && !d.paused && !d.feedback) {
            long now = SystemClock.elapsedRealtime();
            long delta = Math.max(0, now - last);
            last = now;
            d.elapsedMs += delta;
            if (!d.rest) {
              d.exerciseMs += delta;
              d.calories +=
                  exercises.get(d.index).getMetValue() * 3.5 * d.weight / 200.0 * delta / 60000.0;
            }
            if (d.remainingMs > 0)
              d.remainingMs =
                  new com.example.fitnessbalance.service.SessionTimer()
                      .remaining(d.remainingMs, delta, false);
            if (new com.example.fitnessbalance.service.SessionTimer()
                .restFinished(d.rest, d.remainingMs, d.paused)) next();
            render();
          } else last = SystemClock.elapsedRealtime();
          handler.postDelayed(this, 250);
        }
      };

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_active_workout);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                confirmEnd();
              }
            });
    findViewById(R.id.exComplete).setOnClickListener(v -> complete(false));
    findViewById(R.id.exSkip).setOnClickListener(v -> complete(true));
    findViewById(R.id.exPause)
        .setOnClickListener(
            v -> {
              d.paused = !d.paused;
              last = SystemClock.elapsedRealtime();
              d.save(this);
              render();
            });
    findViewById(R.id.exEnd).setOnClickListener(v -> confirmEnd());
    ready = true;
  }

  @Override
  protected void load() {
    if (loaded) {
      render();
      return;
    }
    loading();
    d = SessionDraft.read(this);
    if (d == null || !d.id.equals(getIntent().getStringExtra(IntentKeys.SESSION))) {
      error(new IllegalStateException());
      return;
    }
    if (d.feedback) {
      feedback();
      return;
    }
    repo.items(d.path, d.lesson)
        .addOnSuccessListener(
            this,
            result -> {
              items = result;
              if (d.planned != items.size() || d.index >= items.size()) {
                error(new IllegalStateException());
                return;
              }
              List<Task<Exercise>> refs = new ArrayList<>();
              for (WorkoutExercise item : items)
                refs.add(new ExerciseRepository().get(item.getExerciseId()));
              Tasks.whenAll(refs)
                  .addOnSuccessListener(
                      this,
                      v -> {
                        exercises.clear();
                        for (Task<Exercise> t : refs) exercises.add(t.getResult());
                        loaded = true;
                        loaded();
                        last = SystemClock.elapsedRealtime();
                        render();
                      })
                  .addOnFailureListener(this, this::error);
            })
        .addOnFailureListener(this, this::error);
  }

  private void render() {
    if (!loaded || d.index >= items.size()) return;
    WorkoutExercise item = items.get(d.index);
    Exercise e = exercises.get(d.index);
    text(
        R.id.exPosition,
        getString(R.string.ex_position, d.index + 1, d.planned, d.completed, d.skipped));
    text(R.id.exExerciseName, e.getName());
    text(R.id.exInstructions, e.getDescription() + "\n\n" + String.join("\n", e.getInstructions()));
    text(
        R.id.exTimer,
        d.paused
            ? getString(R.string.ex_paused)
            : d.rest
                ? getString(R.string.ex_rest, (d.remainingMs + 999) / 1000)
                : "duration".equals(item.getTargetType())
                    ? d.remainingMs == 0
                        ? getString(R.string.ex_finished_timer)
                        : getString(R.string.ex_seconds, (d.remainingMs + 999) / 1000)
                    : getString(R.string.ex_repetitions, item.getTargetValue()));
    text(R.id.exPause, getString(d.paused ? R.string.ex_resume : R.string.ex_pause));
    findViewById(R.id.exComplete).setEnabled(!d.paused && !d.rest);
    findViewById(R.id.exSkip).setEnabled(!d.paused && !d.rest);
  }

  private void complete(boolean skip) {
    if (!loaded
        || d.rest
        || d.paused
        || transitioning
        || SystemClock.elapsedRealtime() - lastAction < 500) return;
    lastAction = SystemClock.elapsedRealtime();
    transitioning = true;
    if (skip) d.skipped++;
    else d.completed++;
    WorkoutExercise item = items.get(d.index);
    if (d.index == items.size() - 1) {
      d.save(this);
      feedback();
      return;
    }
    if (item.getRestAfterSeconds() > 0) {
      d.rest = true;
      d.remainingMs = item.getRestAfterSeconds() * 1000L;
    } else next();
    d.save(this);
    transitioning = false;
    render();
  }

  private void next() {
    d.rest = false;
    d.index++;
    if (d.index >= items.size()) {
      feedback();
      return;
    }
    WorkoutExercise item = items.get(d.index);
    d.remainingMs = "duration".equals(item.getTargetType()) ? item.getTargetValue() * 1000L : 0;
    d.save(this);
  }

  private void confirmEnd() {
    if (d == null) return;
    d.paused = true;
    d.save(this);
    if (loaded) render();
    new AlertDialog.Builder(this)
        .setMessage(R.string.ex_end_confirm)
        .setPositiveButton(R.string.ex_end, (a, b) -> feedback())
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void feedback() {
    if (d == null) return;
    d.feedback = true;
    d.paused = true;
    d.save(this);
    Intent i = new Intent(this, WorkoutFeedbackActivity.class);
    i.putExtra(IntentKeys.SESSION, d.id);
    startActivity(i);
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    visible = true;
    last = SystemClock.elapsedRealtime();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    visible = false;
    handler.removeCallbacks(tick);
    if (d != null) {
      d.paused = true;
      d.save(this);
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (d != null) d.save(this);
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
