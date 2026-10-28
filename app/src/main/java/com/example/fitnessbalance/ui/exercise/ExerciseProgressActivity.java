package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class ExerciseProgressActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_progress);
  }

  @Override
  protected void load() {
    loading();
    Task<DocumentSnapshot> profile = repository.profile(),
        week = repository.week(DateUtils.week(new Date(), TimeZone.getDefault()));
    Task<QuerySnapshot> records = repository.recent();
    Tasks.whenAll(profile, week, records)
        .addOnSuccessListener(
            this,
            v -> {
              progress(profile.getResult(), week.getResult());
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              for (DocumentSnapshot r : records.getResult())
                rows.add(
                    new ExerciseRowsAdapter.Row(
                        Values.text(r.get("lessonName"), ""),
                        getString(
                            R.string.ex_history_meta,
                            Values.decimal(r.get("completionPercentage"), 0),
                            Values.integer(r.get("xpEarned"), 0)),
                        r.getTimestamp("completedAt") == null
                            ? ""
                            : DateUtils.day(
                                r.getTimestamp("completedAt").toDate(), TimeZone.getDefault()),
                        () -> openSession(WorkoutResultActivity.class, r.getId())));
              list(R.id.exRecords, new ExerciseRowsAdapter(rows));
              loaded();
              if (rows.isEmpty()) status(R.string.ex_no_records);
            })
        .addOnFailureListener(this, this::error);
  }
}
