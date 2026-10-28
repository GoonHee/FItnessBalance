package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.WorkoutExerciseAdapter;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.repository.ExerciseRepository;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import java.util.*;

public class WorkoutDetailsActivity extends ExerciseBaseActivity {
  private List<WorkoutExercise> items;
  private boolean starting;
  private double weight = 70;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_details);
    findViewById(R.id.exStart).setOnClickListener(v -> start());
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    if (lesson() == null) {
      error(new IllegalArgumentException());
      return;
    }
    Task<WorkoutLesson> l = repo.lesson(path(), lesson());
    Task<com.google.firebase.firestore.DocumentSnapshot> p = repo.progress(path());
    Task<List<WorkoutExercise>> i = repo.items(path(), lesson());
    Task<com.google.firebase.firestore.DocumentSnapshot> profile = repo.profile();
    Tasks.whenAll(l, p, i, profile)
        .addOnSuccessListener(
            this,
            v -> {
              if (!repo.unlocked(l.getResult(), p.getResult())) {
                error(new IllegalStateException());
                return;
              }
              WorkoutLesson model = l.getResult();
              items = i.getResult();
              weight = Values.decimal(profile.getResult().get("weight"), 70);
              if (weight <= 0 || weight > 300) weight = 70;
              text(
                  R.id.exSummary,
                  getString(
                      R.string.ex_lesson_summary,
                      model.getName(),
                      model.getDescription(),
                      model.getDifficulty(),
                      model.getEstimatedMinutes(),
                      model.getBaseXp(),
                      String.join(", ", model.getEquipment())));
              List<Task<Exercise>> refs = new ArrayList<>();
              for (WorkoutExercise item : items)
                refs.add(new ExerciseRepository().get(item.getExerciseId()));
              Tasks.whenAll(refs)
                  .addOnSuccessListener(
                      this,
                      w -> {
                        List<String> rows = new ArrayList<>();
                        for (int n = 0; n < items.size(); n++) {
                          WorkoutExercise item = items.get(n);
                          rows.add(
                              getString(
                                  "duration".equals(item.getTargetType())
                                      ? R.string.ex_target_duration
                                      : R.string.ex_target_reps,
                                  refs.get(n).getResult().getName(),
                                  item.getTargetValue(),
                                  item.getRestAfterSeconds()));
                        }
                        list(R.id.exExercises, new WorkoutExerciseAdapter(rows));
                        loaded();
                      })
                  .addOnFailureListener(this, this::error);
            })
        .addOnFailureListener(this, this::error);
  }

  private void start() {
    if (starting || items == null) return;
    SessionDraft old = SessionDraft.read(this);
    if (old != null) {
      Toast.makeText(this, R.string.ex_finish_existing, Toast.LENGTH_LONG).show();
      navigate(old);
      return;
    }
    starting = true;
    findViewById(R.id.exStart).setEnabled(false);
    SessionDraft draft = new SessionDraft();
    draft.id = UUID.randomUUID().toString();
    draft.path = path();
    draft.lesson = lesson();
    draft.planned = items.size();
    draft.weight = weight;
    draft.remainingMs =
        "duration".equals(items.get(0).getTargetType()) ? items.get(0).getTargetValue() * 1000L : 0;
    repo.begin(draft.path, draft.lesson, draft.id, draft.planned)
        .addOnSuccessListener(
            this,
            v -> {
              draft.save(this);
              starting = false;
              navigate(draft);
            })
        .addOnFailureListener(
            this,
            e -> {
              starting = false;
              findViewById(R.id.exStart).setEnabled(true);
              error(e);
            });
  }

  private void navigate(SessionDraft d) {
    Intent i =
        new Intent(this, d.feedback ? WorkoutFeedbackActivity.class : ActiveWorkoutActivity.class);
    i.putExtra(IntentKeys.SESSION, d.id);
    startActivity(i);
  }
}
