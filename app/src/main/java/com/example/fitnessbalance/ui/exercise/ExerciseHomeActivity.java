package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import com.example.fitnessbalance.health.*;
import androidx.health.connect.client.HealthConnectClient;
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
  private int loadVersion;
  private HealthConnectBridge healthBridge;
  private HealthRecommendationStore healthStore;
  private AdaptiveRecommendationService.Decision decision;
  private String decisionId;
  private boolean settingHealth;
  private boolean usingUsual;


  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_home);
    healthBridge = new HealthConnectBridge(this);
    ((CheckBox)findViewById(R.id.exUseHealth)).setOnCheckedChangeListener((button, checked) -> {
      if (settingHealth || healthStore == null) return;
      if (checked) {
        settingHealth = true; ((CheckBox)button).setChecked(false); settingHealth = false;
        new AlertDialog.Builder(this).setTitle(R.string.ar_use_health)
            .setMessage(R.string.ar_consent)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ar_enable, (d,w) -> setHealthEnabled(true)).show();
      } else setHealthEnabled(false);
    });
    findViewById(R.id.exUsual).setOnClickListener(v -> {
      if (decision != null) displayDecision(decision, true);
    });
    findViewById(R.id.exExplain).setOnClickListener(v -> {
      if (decision == null) return;
      new AlertDialog.Builder(this).setTitle(R.string.ar_explain)
          .setMessage(getString(R.string.ar_details, AdaptiveRecommendationService.VERSION,
              getString(reasonResource(decision.usual.reason)), healthExplanation(decision),
              Values.integer(decision.inputs.get("preferredMinutes"),10), decision.baselineDays))
          .setPositiveButton(android.R.string.ok, null).show();
    });
    new LocalDayObserver(this, this, null, this::reload);
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
              else if (recommended != null) confirmRecommendation();
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
    final int version = ++loadVersion;
    loading();
    healthBridge.cancelPending();
    healthStore = new HealthRecommendationStore(this, uid);
    settingHealth = true;
    ((CheckBox)findViewById(R.id.exUseHealth)).setChecked(healthStore.enabled());
    settingHealth = false;
    decision = null; decisionId = null;
    findViewById(R.id.exUsual).setVisibility(View.GONE);
    text(R.id.exHealthReason, "");
    text(R.id.alHomeSummary, getString(R.string.fl_loading));
    list(R.id.exQuests, new QuestAdapter(Collections.emptyList()));
    recommended = null;
    WorkoutDraft draft = drafts.read(uid);
    enabled(R.id.exStart, draft != null);
    text(R.id.exStart, getString(draft == null ? R.string.ex_start : R.string.ex_resume_session));
    findViewById(R.id.exDiscard).setVisibility(draft == null ? View.GONE : View.VISIBLE);
    Date now = new Date();
    TimeZone zone = TimeZone.getDefault();
    String activityDay = DateUtils.day(now, zone);
    new com.example.fitnessbalance.repository.ExerciseLogRepository(uid)
        .loadDay(activityDay)
        .addOnSuccessListener(
            this,
            day -> {
              if (version != loadVersion
                  || !activityDay.equals(DateUtils.day(new Date(), TimeZone.getDefault()))) return;
              text(
                  R.id.alHomeSummary,
                  getString(
                          R.string.al_total_value,
                          Values.integer(day.totals.get("caloriesBurned"), 0))
                      + "\n"
                      + getString(
                          R.string.al_breakdown,
                          Values.integer(day.totals.get("manualCaloriesBurned"), 0),
                          Values.integer(day.totals.get("structuredCaloriesBurned"), 0))
                      + "\n"
                      + getString(
                          R.string.al_time,
                          Values.decimal(day.totals.get("exerciseActiveSeconds"), 0) / 60,
                          Values.integer(day.totals.get("exerciseEntryCount"), 0)));
            })
        .addOnFailureListener(
            this,
            error -> {
              if (version == loadVersion)
                text(R.id.alHomeSummary, getString(R.string.al_summary_error));
            });

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
              if (version != loadVersion || !activityDay.equals(LocalDayObserver.today())) return;
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
              prepareRecommendation(preferences, available, version);
              if (lessons.getResult().isEmpty()) status(R.string.ex_empty);
              else if (!user.exists()) status(R.string.ex_profile_default);
            })
        .addOnFailureListener(
            this,
            e -> {
              if (version == loadVersion && activityDay.equals(LocalDayObserver.today())) error(e);
            });
  }

  private boolean currentRecommendation(int version) {
    com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
    return version == loadVersion && !isFinishing() && !isDestroyed() && user != null && uid.equals(user.getUid());
  }

  private void setHealthEnabled(boolean enabled) {
    try { healthStore.setEnabled(enabled); reload(); }
    catch (java.io.IOException error) {
      settingHealth = true; ((CheckBox)findViewById(R.id.exUseHealth)).setChecked(healthStore.enabled()); settingHealth = false;
      status(R.string.ar_audit_error);
    }
  }

  private void prepareRecommendation(RecommendationService.Preferences preferences,
      List<WorkoutLesson> available, int version) {
    boolean optIn = healthStore.enabled();
    AdaptiveRecommendationService service = new AdaptiveRecommendationService();
    displayDecision(service.recommend(preferences,available,Collections.emptyMap(),Collections.emptyList(),
        optIn,false,System.currentTimeMillis(),java.time.ZoneId.systemDefault()),false);
    if (!optIn) return;
    try {
      if (HealthConnectBridge.availability(this) != HealthConnectClient.SDK_AVAILABLE) return;
    } catch (RuntimeException unavailable) { return; }
    healthBridge.check(new HealthConnectBridge.Callback() {
      @Override public void onResult(Map<String,? extends Object> ignored, Set<String> granted) {
        if (!currentRecommendation(version)) return;
        boolean permitted = granted.contains(HealthConnectBridge.SLEEP);
        HealthSummaryStore cache = new HealthSummaryStore(ExerciseHomeActivity.this,uid);
        Set<String> allowed = new HashSet<>();
        if (granted.contains(HealthConnectBridge.STEPS)) allowed.add("steps");
        if (permitted) allowed.add("sleep");
        if (granted.contains(HealthConnectBridge.HEART)) allowed.add("heart");
        Map<String,Object> health = HealthSummaryPolicy.mask(cache.read(),allowed);
        try {
          cache.write(health);
          if (!permitted) healthStore.removeSleepInputs();
          else healthStore.observe(health,System.currentTimeMillis(),java.time.ZoneId.systemDefault());
        } catch (java.io.IOException error) { status(R.string.ar_audit_error); }
        displayDecision(service.recommend(preferences,available,health,healthStore.history(),
            true,permitted,System.currentTimeMillis(),java.time.ZoneId.systemDefault()),false);
      }
      @Override public void onError() {
        if (currentRecommendation(version)) text(R.id.exHealthReason, getString(R.string.ar_permission_fallback));
      }
    });
  }

  private void displayDecision(AdaptiveRecommendationService.Decision result, boolean usual) {
    decision = result; usingUsual = usual;
    RecommendationService.Recommendation selected = usual ? result.usual : result.suggested;
    recommended = selected.lesson;
    text(R.id.exRecommendationTitle, recommended == null ? getString(R.string.ex_no_recommendation) : recommended.getName());
    text(R.id.exRecommendationMeta, recommended == null ? "" : getString(R.string.ex_recommendation_meta,
        recommended.getEstimatedMinutes(),recommended.getBaseXp()));
    text(R.id.exRecommendationReason, getString(reasonResource(selected.reason)));
    text(R.id.exHealthReason, usual ? getString(R.string.ar_chose_usual) : healthExplanation(result));
    findViewById(R.id.exUsual).setVisibility(result.adjusted() && !usual ? View.VISIBLE : View.GONE);
    enabled(R.id.exStart, drafts.read(uid) != null || recommended != null);
    try { decisionId = healthStore.record(result.audit(System.currentTimeMillis(),usual)); }
    catch (java.io.IOException error) { decisionId = null; status(R.string.ar_audit_error); }
  }

  private String healthExplanation(AdaptiveRecommendationService.Decision result) {
    switch(result.healthReason) {
      case "disabled": return getString(R.string.ar_disabled);
      case "permission_unavailable": return getString(R.string.ar_permission_fallback);
      case "missing_or_stale": return getString(R.string.ar_stale_fallback);
      case "building_baseline": return getString(R.string.ar_building, result.baselineDays);
      case "sleep_shortfall": return getString(R.string.ar_sleep_shortfall,
          result.currentSleep/3600.0,result.baselineSleep/3600.0);
      case "priority": return getString(R.string.ar_priority);
      case "no_shorter": return getString(R.string.ar_no_shorter);
      default: return getString(R.string.ar_no_adjustment);
    }
  }

  private void confirmRecommendation() {
    final WorkoutLesson selected = recommended;
    final String audit = decisionId;
    final int version = loadVersion;
    new AlertDialog.Builder(this).setTitle(R.string.ar_confirm_title)
        .setMessage(getString(R.string.ar_confirm, selected.getName(), selected.getEstimatedMinutes(),
            usingUsual ? getString(R.string.ar_chose_usual) : decision == null ? "" : healthExplanation(decision)))
        .setNegativeButton(R.string.cancel,null)
        .setPositiveButton(R.string.ar_review,(dialog,which) -> {
          if (!currentRecommendation(version)) return;
          try { healthStore.confirm(audit); }
          catch(java.io.IOException error) { android.widget.Toast.makeText(this,R.string.ar_audit_error,android.widget.Toast.LENGTH_LONG).show(); }
          open(WorkoutDetailsActivity.class,selected.getId());
        }).show();
  }

  @Override protected void onStop() {
    loadVersion++; if (healthBridge != null) healthBridge.cancelPending(); super.onStop();
  }
  @Override protected void onDestroy() { if (healthBridge != null) healthBridge.close(); super.onDestroy(); }

  private int reasonResource(String reason) {
    switch (reason) {
      case "sleep_shortfall":
        return R.string.ar_shorter_reason;
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
