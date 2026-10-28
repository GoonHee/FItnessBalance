package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.TextRowsAdapter;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class ExerciseProgressActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_progress);
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    Task<DocumentSnapshot> p = repo.profile();
    Task<QuerySnapshot> records = repo.records();
    Tasks.whenAll(p, records)
        .addOnSuccessListener(
            this,
            v -> {
              int xp = Values.integer(p.getResult().get("totalXp"), 0);
              GamificationService g = new GamificationService();
              MomentumService m = new MomentumService();
              int target = m.target(Values.integer(p.getResult().get("weeklyWorkoutTarget"), 3));
              int count =
                  Values.integer(
                      Values.map(p.getResult().get("weeklyCounts"))
                          .get(DateUtils.week(new Date(), TimeZone.getDefault())),
                      0);
              text(
                  R.id.exSummary,
                  getString(
                      R.string.ex_progress_summary,
                      getString(R.string.ex_level_xp, g.level(xp), xp, g.toNextLevel(xp)),
                      getString(R.string.ex_weekly),
                      getString(R.string.ex_momentum, count, target, m.percentage(count, target))));
              List<String> rows = new ArrayList<>();
              for (DocumentSnapshot r : records.getResult())
                rows.add(
                    getString(
                        R.string.ex_record_row,
                        Values.text(r.get("lessonName"), ""),
                        Values.decimal(r.get("completionPercentage"), 0),
                        Values.integer(r.get("xpEarned"), 0)));
              list(R.id.exRecords, new TextRowsAdapter(rows));
              loaded();
              if (rows.isEmpty()) message(R.string.ex_no_records);
            })
        .addOnFailureListener(this, this::error);
  }
}
