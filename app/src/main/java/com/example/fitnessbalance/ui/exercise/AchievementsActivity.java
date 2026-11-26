package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class AchievementsActivity extends ExerciseBaseActivity {
  private DailyChestDialog chests;

  private static final int[] CATEGORIES = {
    R.string.gm_milestone, R.string.gm_consistency, R.string.gm_exploration,
    R.string.gm_reflection, R.string.gm_balance, R.string.gm_comeback, R.string.gm_other_badges
  };

  private static int category(String id) {
    switch (id) {
      case "first_step": return 0;
      case "routine_builder": return 1;
      case "path_explorer": return 2;
      case "honest_reflection": return 3;
      case "balanced_week": return 4;
      case "welcome_back": return 5;
      default: return 6;
    }
  }
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_achievements);
  }

  @Override
  protected void onStop() {
    if (chests != null) chests.onHostStop();
    super.onStop();
  }

  @Override
  protected void load() {
    loading();
    if (chests == null) chests = new DailyChestDialog(this, uid);
    Task<QuerySnapshot> definitions = repository.achievements(),
        unlocked = repository.unlockedAchievements();
    Tasks.whenAll(definitions, unlocked)
        .addOnSuccessListener(
            this,
            v -> {
              Set<String> ids = new HashSet<>();
              for (DocumentSnapshot a : unlocked.getResult()) ids.add(a.getId());
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              List<DocumentSnapshot> badges = definitions.getResult().getDocuments();
              badges = new ArrayList<>(badges);
              badges.sort(Comparator.comparingInt(a -> category(a.getId())));
              for (DocumentSnapshot badge : badges) {
                String id = badge.getId(), name = Values.text(badge.get("name"), "");
                boolean earned = ids.contains(id);
                String state = getString(earned ? R.string.ex_earned : R.string.ex_not_earned);
                if (earned) state = getString(R.string.dc_chest_row, state);
                rows.add(new ExerciseRowsAdapter.Row(name, Values.text(badge.get("description"), ""),
                    getString(R.string.gm_badge_state, getString(CATEGORIES[category(id)]), state),
                    earned ? () -> chests.showAchievement(id, name) : null));
              }
              list(R.id.exAchievementsList, new AchievementAdapter(rows));
              loaded();
              if (rows.isEmpty()) status(R.string.ex_no_achievements);
            })
        .addOnFailureListener(this, this::error);
  }
}
