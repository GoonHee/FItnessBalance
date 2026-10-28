package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.AchievementAdapter;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class AchievementsActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_achievements);
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    Task<QuerySnapshot> shared = repo.achievements(), owned = repo.unlockedAchievements();
    Tasks.whenAll(shared, owned)
        .addOnSuccessListener(
            this,
            v -> {
              Set<String> ids = new HashSet<>();
              for (DocumentSnapshot d : owned.getResult()) ids.add(d.getId());
              List<String> rows = new ArrayList<>();
              for (DocumentSnapshot d : shared.getResult())
                rows.add(
                    getString(
                        R.string.ex_achievement_row,
                        Values.text(d.get("name"), d.getId()),
                        getString(
                            ids.contains(d.getId()) ? R.string.ex_earned : R.string.ex_not_earned),
                        Values.text(d.get("description"), "")));
              list(R.id.exAchievementList, new AchievementAdapter(rows));
              loaded();
              if (rows.isEmpty()) message(R.string.ex_no_achievements);
            })
        .addOnFailureListener(this, this::error);
  }
}
