package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;

public final class WorkoutPathActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_path);
  }

  @Override
  protected void load() {
    loading();
    Task<List<WorkoutLesson>> lessons = repository.lessons(path());
    Task<DocumentSnapshot> progress = repository.progress(path());
    Tasks.whenAll(lessons, progress)
        .addOnSuccessListener(
            this,
            v -> {
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              Map<String, Object> best = Values.map(progress.getResult().get("bestPercent")),
                  stars = Values.map(progress.getResult().get("bestStars"));
              for (WorkoutLesson lesson : lessons.getResult()) {
                if (lesson.getStandalone()) continue;
                boolean unlocked = repository.unlocked(lesson, progress.getResult());
                double percentage = Values.decimal(best.get(lesson.getId()), 0);
                String state =
                    getString(
                            percentage >= 50
                                ? R.string.ex_done
                                : unlocked ? R.string.ex_unlocked : R.string.ex_locked)
                        + "\n"
                        + getString(
                            R.string.ex_best,
                            Values.integer(stars.get(lesson.getId()), 0),
                            percentage);
                rows.add(
                    new ExerciseRowsAdapter.Row(
                        lesson.getName(),
                        getString(
                            R.string.ex_lesson_meta,
                            lesson.getStageName(),
                            lesson.getEstimatedMinutes(),
                            lesson.getBaseXp()),
                        state,
                        unlocked
                            ? () -> open(WorkoutDetailsActivity.class, lesson.getId())
                            : null));
              }
              list(R.id.exLessons, new WorkoutLessonAdapter(rows));
              loaded();
              if (rows.isEmpty()) status(R.string.ex_empty);
            })
        .addOnFailureListener(this, this::error);
  }
}
