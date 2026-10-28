package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.WorkoutLessonAdapter;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;

public class WorkoutPathActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_path);
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    Task<List<WorkoutLesson>> lessons = repo.lessons(path());
    Task<DocumentSnapshot> progress = repo.progress(path());
    Tasks.whenAll(lessons, progress)
        .addOnSuccessListener(
            this,
            v -> {
              List<WorkoutLesson> rows = new ArrayList<>();
              for (WorkoutLesson l : lessons.getResult()) if (!l.getStandalone()) rows.add(l);
              list(
                  R.id.exLessons,
                  new WorkoutLessonAdapter(
                      rows,
                      Values.map(progress.getResult().get("bestPercent")),
                      Values.map(progress.getResult().get("bestStars")),
                      l -> open(WorkoutDetailsActivity.class, l.getId())));
              loaded();
              if (rows.isEmpty()) message(R.string.ex_empty);
            })
        .addOnFailureListener(this, this::error);
  }
}
