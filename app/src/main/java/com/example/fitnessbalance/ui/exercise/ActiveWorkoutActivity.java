package com.example.fitnessbalance.ui.exercise;

import android.os.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.SessionState;
import com.example.fitnessbalance.util.IntentKeys;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public final class ActiveWorkoutActivity extends ExerciseBaseActivity {
  private WorkoutDraft draft;
  private final Handler timer = new Handler(Looper.getMainLooper());
  private long lastTick, lastAction, lastSave;
  private boolean foreground, firstLoad = true, autoStart, confirmed;
  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (confirmed
              && draft != null
              && foreground
              && !draft.state.paused
              && !SessionState.FEEDBACK.equals(draft.state.phase)) {
            long now = SystemClock.elapsedRealtime();
            long delta = Math.max(0, now - lastTick);
            boolean active = SessionState.EXERCISE.equals(draft.state.phase);
            lastTick = now;
            draft.state.tick(delta, draft.plan.items);
            if (active)
              draft.calories +=
                  draft.plan.exercises.get(draft.state.index).getMetValue()
                      * 3.5
                      * draft.weight
                      / 200.0
                      * delta
                      / 60000.0;
            if (now - lastSave >= 1000) {
              drafts.save(draft);
              lastSave = now;
            }
            render();
          } else lastTick = SystemClock.elapsedRealtime();
          timer.postDelayed(this, 250);
        }
      };

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_active_workout);
    autoStart = b == null && getIntent().getBooleanExtra(IntentKeys.START_NEW, false);
    findViewById(R.id.exComplete).setOnClickListener(v -> complete(false));
    findViewById(R.id.exSkip).setOnClickListener(v -> complete(true));
    findViewById(R.id.exPause)
        .setOnClickListener(
            v -> {
              if (draft == null) return;
              draft.state.paused = !draft.state.paused;
              lastTick = SystemClock.elapsedRealtime();
              drafts.save(draft);
              render();
            });
    findViewById(R.id.exEnd).setOnClickListener(v -> end());
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (draft == null) finish();
                else end();
              }
            });
  }

  @Override
  protected void load() {
    loading();
    confirmed = false;
    enabled(R.id.exPause, false);
    enabled(R.id.exEnd, false);
    enabled(R.id.exComplete, false);
    enabled(R.id.exSkip, false);
    if (firstLoad) {
      draft = drafts.read(uid);
      firstLoad = false;
    }
    if (draft == null || !draft.sessionId.equals(getIntent().getStringExtra(IntentKeys.SESSION))) {
      loaded();
      status(R.string.ex_session_missing);
      return;
    }
    if (SessionState.FEEDBACK.equals(draft.state.phase)) {
      feedback();
      return;
    }
    repository
        .begin(draft.pathId, draft.plan.lesson.getId(), draft.sessionId, draft.plan.items.size())
        .addOnSuccessListener(
            this,
            v -> {
              confirmed = true;
              loaded();
              if (autoStart) {
                draft.state.paused = false;
                autoStart = false;
              }
              lastTick = SystemClock.elapsedRealtime();
              enabled(R.id.exPause, true);
              enabled(R.id.exEnd, true);
              render();
            })
        .addOnFailureListener(this, this::error);
  }

  private void render() {
    if (draft == null) return;
    SessionState s = draft.state;
    WorkoutExercise item = draft.plan.items.get(s.index);
    Exercise e = draft.plan.exercises.get(s.index);
    text(R.id.exPosition, getString(R.string.ex_position, s.index + 1, draft.plan.items.size()));
    text(R.id.exExerciseName, e.getName());
    text(R.id.exInstructions, e.getDescription() + "\n\n" + String.join("\n", e.getInstructions()));
    text(R.id.exSessionCounts, getString(R.string.ex_session_counts, s.completed, s.skipped));
    ((LinearProgressIndicator) findViewById(R.id.exSessionBar))
        .setProgressCompat(100 * (s.completed + s.skipped) / draft.plan.items.size(), false);
    String timerText =
        s.paused
            ? getString(R.string.ex_paused)
            : SessionState.REST.equals(s.phase)
                ? getString(R.string.ex_rest, (s.remainingMs + 999) / 1000)
                : "duration".equals(item.getTargetType())
                    ? s.remainingMs > 0
                        ? getString(R.string.ex_countdown, (s.remainingMs + 999) / 1000)
                        : getString(R.string.ex_time_reached)
                    : getString(R.string.ex_rep_target, item.getTargetValue());
    text(R.id.exTimer, timerText);
    text(R.id.exPause, getString(s.paused ? R.string.ex_resume : R.string.ex_pause));
    boolean running = confirmed && !s.paused && SessionState.EXERCISE.equals(s.phase);
    enabled(R.id.exComplete, running);
    enabled(R.id.exSkip, running);
  }

  private void complete(boolean skip) {
    if (!confirmed || draft == null || SystemClock.elapsedRealtime() - lastAction < 500) return;
    lastAction = SystemClock.elapsedRealtime();
    if (draft.state.complete(skip, draft.plan.items)) {
      drafts.save(draft);
      if (SessionState.FEEDBACK.equals(draft.state.phase)) feedback();
      else render();
    }
  }

  private void end() {
    if (draft == null) return;
    draft.state.paused = true;
    drafts.save(draft);
    render();
    new AlertDialog.Builder(this)
        .setMessage(R.string.ex_end_confirm)
        .setPositiveButton(
            R.string.ex_end,
            (d, w) -> {
              draft.state.end();
              drafts.save(draft);
              feedback();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void feedback() {
    openSession(WorkoutFeedbackActivity.class, draft.sessionId);
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    lastTick = SystemClock.elapsedRealtime();
    timer.removeCallbacks(tick);
    timer.post(tick);
  }

  @Override
  protected void onPause() {
    foreground = false;
    timer.removeCallbacks(tick);
    if (draft != null) {
      draft.state.paused = true;
      drafts.save(draft);
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    if (draft != null) drafts.save(draft);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    timer.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
