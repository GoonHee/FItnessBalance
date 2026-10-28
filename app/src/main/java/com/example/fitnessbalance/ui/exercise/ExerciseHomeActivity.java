package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import com.example.fitnessbalance.ExerciseActivity;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.QuestAdapter;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class ExerciseHomeActivity extends ExerciseBaseActivity {
  private WorkoutLesson recommended;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_home);
    findViewById(R.id.exStart)
        .setOnClickListener(
            v -> {
              SessionDraft d = SessionDraft.read(this);
              if (d != null) {
                Intent i =
                    new Intent(
                        this,
                        d.feedback ? WorkoutFeedbackActivity.class : ActiveWorkoutActivity.class);
                i.putExtra(IntentKeys.SESSION, d.id);
                startActivity(i);
              } else if (recommended != null)
                open(WorkoutDetailsActivity.class, recommended.getId());
            });
    findViewById(R.id.exPath).setOnClickListener(v -> open(WorkoutPathActivity.class, null));
    findViewById(R.id.exQuick).setOnClickListener(v -> open(ExerciseActivity.class, null));
    findViewById(R.id.exProgress)
        .setOnClickListener(v -> open(ExerciseProgressActivity.class, null));
    findViewById(R.id.exAchievements)
        .setOnClickListener(v -> open(AchievementsActivity.class, null));
    findViewById(R.id.exDiscard)
        .setOnClickListener(
            v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.ex_discard_confirm)
                    .setPositiveButton(
                        R.string.ex_discard,
                        (dialog, which) -> {
                          SessionDraft current = SessionDraft.read(this);
                          if (current != null) SessionDraft.clear(this, current.id);
                          load();
                        })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
    ready = true;
  }

  @Override
  protected void load() {
    loading();
    Task<DocumentSnapshot> profile = repo.profile(), progress = repo.progress(path());
    Task<List<WorkoutLesson>> lessons = repo.lessons(path());
    Task<QuerySnapshot> quests = repo.quests(), records = repo.records();
    Tasks.whenAll(profile, progress, lessons, quests, records)
        .addOnSuccessListener(
            this,
            v -> {
              DocumentSnapshot user = profile.getResult();
              int xp = Values.integer(user.get("totalXp"), 0);
              GamificationService g = new GamificationService();
              text(
                  R.id.exHello,
                  getString(
                      R.string.ex_hello, Values.text(user.get("fullName"), "FitnessBalance")));
              text(
                  R.id.exStats,
                  getString(R.string.ex_level_xp, g.level(xp), xp, g.toNextLevel(xp)));
              MomentumService m = new MomentumService();
              int target = m.target(Values.integer(user.get("weeklyWorkoutTarget"), 3));
              int count =
                  Values.integer(
                      Values.map(user.get("weeklyCounts"))
                          .get(DateUtils.week(new Date(), TimeZone.getDefault())),
                      0);
              int percentage = m.percentage(count, target);
              text(R.id.exMomentum, getString(R.string.ex_momentum, count, target, percentage));
              text(
                  R.id.exBand,
                  getString(
                      percentage >= 80
                          ? R.string.ex_strong
                          : percentage >= 50
                              ? R.string.ex_building
                              : percentage > 0 ? R.string.ex_restart : R.string.ex_begin));
              Set<String> done = new HashSet<>();
              for (DocumentSnapshot q : quests.getResult())
                if (Boolean.TRUE.equals(q.get("completed"))) done.add(q.getString("questId"));
              List<String> rows = new ArrayList<>();
              int[] labels = {
                R.string.ex_quest_planned, R.string.ex_quest_minutes, R.string.ex_quest_feedback
              };
              String[] ids = {"planned", "minutes", "feedback"};
              for (int n = 0; n < ids.length; n++)
                rows.add(
                    getString(
                        R.string.ex_quest_row,
                        getString(
                            done.contains(ids[n])
                                ? R.string.ex_done_symbol
                                : R.string.ex_pending_symbol),
                        getString(labels[n])));
              list(R.id.exQuests, new QuestAdapter(rows));
              List<WorkoutLesson> available = new ArrayList<>();
              Map<String, Object> best = Values.map(progress.getResult().get("bestPercent"));
              for (WorkoutLesson l : lessons.getResult())
                if (repo.unlocked(l, progress.getResult())
                    && (l.getStandalone() || Values.decimal(best.get(l.getId()), 0) < 50))
                  available.add(l);
              boolean hasNormal = false;
              for (WorkoutLesson candidate : available)
                if (!candidate.getStandalone()) hasNormal = true;
              if (!hasNormal)
                for (WorkoutLesson l : lessons.getResult())
                  if (!l.getStandalone() && repo.unlocked(l, progress.getResult()))
                    available.add(l);
              List<Map<String, Object>> recent = new ArrayList<>();
              for (DocumentSnapshot record : records.getResult()) {
                Map<String, Object> data = new HashMap<>(record.getData());
                if (record.getTimestamp("completedAt") != null)
                  data.put("completedDate", record.getTimestamp("completedAt").toDate());
                recent.add(data);
              }
              RecommendationService.Context context =
                  new RecommendationContextFactory()
                      .create(
                          user.getData() == null ? Collections.emptyMap() : user.getData(),
                          recent,
                          new Date(),
                          TimeZone.getDefault(),
                          user.getTimestamp("lastWorkoutAt") == null
                              ? null
                              : user.getTimestamp("lastWorkoutAt").toDate(),
                          user.getTimestamp("lastDiscomfortAt") == null
                              ? null
                              : user.getTimestamp("lastDiscomfortAt").toDate());
              RecommendationService.Recommendation recommendation =
                  new RecommendationService().recommend(context, available);
              recommended = recommendation.lesson;
              int reason = reasonResource(recommendation.reason);
              text(
                  R.id.exRecommendation,
                  recommended == null
                      ? getString(R.string.ex_no_recommendation)
                      : getString(
                          R.string.ex_recommendation,
                          recommended.getName(),
                          recommended.getEstimatedMinutes(),
                          recommended.getBaseXp(),
                          getString(reason)));

              SessionDraft draft = SessionDraft.read(this);
              findViewById(R.id.exDiscard)
                  .setVisibility(
                      draft == null ? android.view.View.GONE : android.view.View.VISIBLE);
              findViewById(R.id.exStart).setEnabled(draft != null || recommended != null);
              text(
                  R.id.exStart,
                  getString(draft == null ? R.string.ex_start : R.string.ex_resume_workout));
              loaded();
              if (!user.exists()) message(R.string.ex_profile_missing);
              if (lessons.getResult().isEmpty()) message(R.string.ex_empty);
            })
        .addOnFailureListener(this, this::error);
  }

  private int reasonResource(String reason) {
    switch (reason) {
      case "discomfort":
        return R.string.ex_reason_discomfort;
      case "recovery":
        return R.string.ex_reason_recovery;
      case "comeback":
        return R.string.ex_reason_comeback;
      case "equipment":
        return R.string.ex_reason_equipment;
      case "easier":
        return R.string.ex_reason_easier;
      case "balance":
        return R.string.ex_reason_balance;
      case "next":
        return R.string.ex_reason_next;
      case "micro":
        return R.string.ex_reason_micro;
      default:
        return R.string.ex_reason_preference;
    }
  }
}
