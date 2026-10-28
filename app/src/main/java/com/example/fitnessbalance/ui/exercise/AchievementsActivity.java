package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class AchievementsActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_achievements);
  }

  @Override
  protected void load() {
    loading();
    Task<QuerySnapshot> definitions = repository.achievements(),
        unlocked = repository.unlockedAchievements();
    Tasks.whenAll(definitions, unlocked)
        .addOnSuccessListener(
            this,
            v -> {
              Set<String> ids = new HashSet<>();
              for (DocumentSnapshot a : unlocked.getResult()) ids.add(a.getId());
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              for (DocumentSnapshot a : definitions.getResult())
                rows.add(
                    new ExerciseRowsAdapter.Row(
                        Values.text(a.get("name"), ""),
                        Values.text(a.get("description"), ""),
                        getString(
                            ids.contains(a.getId()) ? R.string.ex_earned : R.string.ex_not_earned),
                        null));
              list(R.id.exAchievementsList, new AchievementAdapter(rows));
              loaded();
              if (rows.isEmpty()) status(R.string.ex_no_achievements);
            })
        .addOnFailureListener(this, this::error);
  }
}
