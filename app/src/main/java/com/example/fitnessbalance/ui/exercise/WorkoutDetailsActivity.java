package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;

public final class WorkoutDetailsActivity extends ExerciseBaseActivity {
  private LessonPlan plan;
  private boolean starting;
  private double weight = 70;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_details);
    findViewById(R.id.exStart).setOnClickListener(v -> start());
  }

  @Override
  protected void load() {
    starting = false;
    loading();
    enabled(R.id.exStart, false);
    String id = getIntent().getStringExtra(IntentKeys.LESSON);
    if (id == null) {
      error(new IllegalStateException());
      return;
    }
    Task<LessonPlan> content = repository.plan(path(), id);
    Task<DocumentSnapshot> progress = repository.progress(path()), profile = repository.profile();
    Tasks.whenAll(content, progress, profile)
        .addOnSuccessListener(
            this,
            v -> {
              plan = content.getResult();
              WorkoutLesson l = plan.lesson;
              boolean unlocked = repository.unlocked(l, progress.getResult());
              weight = Values.decimal(profile.getResult().get("weight"), 70);
              if (weight <= 0 || weight > 300) weight = 70;
              text(R.id.exLessonName, l.getName());
              text(
                  R.id.exLessonSummary,
                  getString(
                      R.string.ex_details_summary,
                      l.getDescription(),
                      l.getDifficulty(),
                      l.getEstimatedMinutes(),
                      l.getBaseXp(),
                      String.join(", ", l.getEquipment())));
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              for (int n = 0; n < plan.items.size(); n++) {
                WorkoutExercise item = plan.items.get(n);
                Exercise exercise = plan.exercises.get(n);
                rows.add(
                    new ExerciseRowsAdapter.Row(
                        exercise.getName(),
                        getString(
                            "duration".equals(item.getTargetType())
                                ? R.string.ex_target_seconds
                                : R.string.ex_target_repetitions,
                            item.getTargetValue(),
                            item.getRestAfterSeconds()),
                        "",
                        null));
              }
              list(R.id.exExercises, new WorkoutExerciseAdapter(rows));
              enabled(R.id.exStart, unlocked && !starting);
              loaded();
              if (!unlocked) status(R.string.ex_locked);
            })
        .addOnFailureListener(this, this::error);
  }

  private void start() {
    if (starting || plan == null) return;
    WorkoutDraft old = drafts.read(uid);
    if (old != null) {
      Toast.makeText(this, R.string.ex_busy_session, Toast.LENGTH_LONG).show();
      openSession(
          com.example.fitnessbalance.service.SessionState.FEEDBACK.equals(old.state.phase)
              ? WorkoutFeedbackActivity.class
              : ActiveWorkoutActivity.class,
          old.sessionId);
      return;
    }
    starting = true;
    enabled(R.id.exStart, false);
    WorkoutDraft draft = new WorkoutDraft();
    draft.userId = uid;
    draft.sessionId = UUID.randomUUID().toString();
    draft.pathId = path();
    draft.plan = plan;
    draft.weight = weight;
    draft.state.initialize(plan.items);
    draft.state.paused = false;
    if (!drafts.save(draft)) {
      starting = false;
      enabled(R.id.exStart, true);
      error(new IllegalStateException());
      return;
    }
    try { new com.example.fitnessbalance.repository.BanditStore(this,uid).bind(
        getIntent().getStringExtra(com.example.fitnessbalance.repository.BanditStore.EXTRA),draft.sessionId,plan.lesson.getId()); }
    catch(java.io.IOException error){Toast.makeText(this,R.string.cmab_error,Toast.LENGTH_LONG).show();}
    repository
        .begin(draft.pathId, plan.lesson.getId(), draft.sessionId, plan.items.size())
        .addOnSuccessListener(
            this,
            v -> {
              starting = false;
              startActivity(
                  new Intent(this, ActiveWorkoutActivity.class)
                      .putExtra(IntentKeys.SESSION, draft.sessionId)
                      .putExtra(IntentKeys.START_NEW, true));
            })
        .addOnFailureListener(
            this,
            e -> {
              drafts.clear(draft.userId, draft.sessionId);
              starting = false;
              enabled(R.id.exStart, true);
              error(e);
            });
  }
}
