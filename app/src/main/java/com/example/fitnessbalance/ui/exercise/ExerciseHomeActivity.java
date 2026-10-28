package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.example.fitnessbalance.ExerciseActivity;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class ExerciseHomeActivity extends ExerciseBaseActivity {
  private WorkoutLesson recommended;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_home);
    findViewById(R.id.exPath).setOnClickListener(v -> open(WorkoutPathActivity.class, null));
    findViewById(R.id.exQuick).setOnClickListener(v -> open(ExerciseActivity.class, null));
    findViewById(R.id.exProgress)
        .setOnClickListener(v -> open(ExerciseProgressActivity.class, null));
    findViewById(R.id.exAchievements)
        .setOnClickListener(v -> open(AchievementsActivity.class, null));
    findViewById(R.id.exStart)
        .setOnClickListener(
            v -> {
              WorkoutDraft d = drafts.read(uid);
              if (d != null)
                openSession(
                    SessionState.FEEDBACK.equals(d.state.phase)
                        ? WorkoutFeedbackActivity.class
                        : ActiveWorkoutActivity.class,
                    d.sessionId);
              else if (recommended != null) open(WorkoutDetailsActivity.class, recommended.getId());
            });
    findViewById(R.id.exDiscard)
        .setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setMessage(R.string.ex_discard_confirm)
                    .setPositiveButton(
                        R.string.ex_discard,
                        (dialog, which) -> {
                          WorkoutDraft d = drafts.read(uid);
                          if (d != null) drafts.clear(uid, d.sessionId);
                          reload();
                        })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
  }

  @Override
  protected void load() {
    loading();
    recommended = null;
    WorkoutDraft draft = drafts.read(uid);
    enabled(R.id.exStart, draft != null);
    text(R.id.exStart, getString(draft == null ? R.string.ex_start : R.string.ex_resume_session));
    findViewById(R.id.exDiscard).setVisibility(draft == null ? View.GONE : View.VISIBLE);
    Date now = new Date();
    TimeZone zone = TimeZone.getDefault();
    Task<DocumentSnapshot> profile = repository.profile(),
        progress = repository.progress(path()),
        week = repository.week(DateUtils.week(now, zone));
    Task<List<WorkoutLesson>> lessons = repository.lessons(path());
    Task<QuerySnapshot> records = repository.recent(),
        quests = repository.dailyQuests(DateUtils.day(now, zone));
    Tasks.whenAll(profile, progress, week, lessons, records, quests)
        .addOnSuccessListener(
            this,
            v -> {
              DocumentSnapshot user = profile.getResult();
              text(
                  R.id.exHello,
                  getString(
                      R.string.ex_hello, Values.text(user.get("fullName"), "FitnessBalance")));
              progress(user, week.getResult());
              List<WorkoutLesson> available = new ArrayList<>(), repeatable = new ArrayList<>();
              Map<String, Object> best = Values.map(progress.getResult().get("bestPercent"));
              for (WorkoutLesson lesson : lessons.getResult())
                if (repository.unlocked(lesson, progress.getResult())) {
                  if (lesson.getStandalone() || Values.decimal(best.get(lesson.getId()), 0) < 50)
                    available.add(lesson);
                  if (!lesson.getStandalone()) repeatable.add(lesson);
                }
              boolean unfinished = false;
              for (WorkoutLesson l : available) if (!l.getStandalone()) unfinished = true;
              if (!unfinished) available.addAll(repeatable);
              List<Map<String, Object>> recent = new ArrayList<>();
              for (DocumentSnapshot r : records.getResult()) {
                Map<String, Object> data = new HashMap<>(r.getData());
                if (r.getTimestamp("completedAt") != null)
                  data.put("completedDate", r.getTimestamp("completedAt").toDate());
                recent.add(data);
              }
              RecommendationService.Preferences preferences =
                  new RecommendationPreferences()
                      .from(
                          user.getData() == null ? Collections.emptyMap() : user.getData(),
                          recent,
                          user.getTimestamp("lastWorkoutAt") == null
                              ? null
                              : user.getTimestamp("lastWorkoutAt").toDate(),
                          user.getTimestamp("lastDiscomfortAt") == null
                              ? null
                              : user.getTimestamp("lastDiscomfortAt").toDate(),
                          now,
                          zone);
              RecommendationService.Recommendation recommendation =
                  new RecommendationService().recommend(preferences, available);
              recommended = recommendation.lesson;
              text(
                  R.id.exRecommendationTitle,
                  recommended == null
                      ? getString(R.string.ex_no_recommendation)
                      : recommended.getName());
              text(
                  R.id.exRecommendationMeta,
                  recommended == null
                      ? ""
                      : getString(
                          R.string.ex_recommendation_meta,
                          recommended.getEstimatedMinutes(),
                          recommended.getBaseXp()));
              text(R.id.exRecommendationReason, getString(reasonResource(recommendation.reason)));
              enabled(R.id.exStart, draft != null || recommended != null);

              Set<String> done = new HashSet<>();
              for (DocumentSnapshot q : quests.getResult())
                if (Boolean.TRUE.equals(q.get("completed"))) done.add(q.getString("questId"));
              String[] ids = {"planned", "minutes", "feedback"};
              int[] labels = {
                R.string.ex_quest_planned, R.string.ex_quest_minutes, R.string.ex_quest_feedback
              };
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              for (int n = 0; n < 3; n++)
                rows.add(
                    new ExerciseRowsAdapter.Row(
                        getString(labels[n]),
                        "",
                        getString(done.contains(ids[n]) ? R.string.ex_done : R.string.ex_pending),
                        null));
              list(R.id.exQuests, new QuestAdapter(rows));
              loaded();
              if (lessons.getResult().isEmpty()) status(R.string.ex_empty);
              else if (!user.exists()) status(R.string.ex_profile_default);
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
      case "easier":
        return R.string.ex_reason_easier;
      case "balance":
        return R.string.ex_reason_balance;
      case "next":
        return R.string.ex_reason_next;
      case "micro":
        return R.string.ex_reason_micro;
      case "equipment":
        return R.string.ex_reason_equipment;
      case "beginner":
        return R.string.ex_reason_beginner;
      default:
        return R.string.ex_reason_preference;
    }
  }
}
