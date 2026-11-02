package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutRecord;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.*;

public final class WorkoutResultActivity extends ExerciseBaseActivity {
  private WorkoutRecord celebration;
  private String celebrationSession,celebrationOwner;
  private boolean celebrationHandled;
  private androidx.appcompat.app.AlertDialog celebrationDialog;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_workout_result);
    celebrationHandled=b!=null&&b.getBoolean("celebrationHandled");
    findViewById(R.id.exContinue)
        .setOnClickListener(
            v -> {
              startActivity(
                  new Intent(this, ExerciseHomeActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
              finish();
            });
  }

  @Override
  protected void load() {
    celebration=null;
    final String account=uid;
    loading();
    String id = getIntent().getStringExtra(IntentKeys.SESSION);
    if (id == null) {
      error(new IllegalStateException());
      return;
    }
    Task<DocumentSnapshot> saved = repository.user().collection(com.example.fitnessbalance.util.FirestoreCollections.RECORDS).document(id)
            .get(com.google.firebase.firestore.Source.SERVER),
        profile = repository.profile(),
        week = repository.week(DateUtils.week(new Date(), TimeZone.getDefault()));
    Tasks.whenAll(saved, profile, week)
        .addOnSuccessListener(
            this,
            v -> {
              com.google.firebase.auth.FirebaseUser active=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
              if(active==null||!account.equals(active.getUid())||!account.equals(uid))return;
              WorkoutRecord r = saved.getResult().toObject(WorkoutRecord.class);
              if (r == null) {
                error(new IllegalStateException());
                return;
              }
              text(
                  R.id.exResultTitle,
                  getString(
                      r.getCompletionPercentage() >= 50
                          ? R.string.ex_finished
                          : R.string.ex_attempted));
              text(R.id.exLessonName, r.getLessonName());
              text(
                  R.id.exResultStats,
                  getString(
                      R.string.ex_result_stats,
                      r.getCompletionPercentage(),
                      r.getCompletedExercises(),
                      r.getPlannedExercises(),
                      r.getDurationSeconds(),
                      r.getActiveDurationSeconds(),
                      r.getEstimatedCaloriesBurned(),
                      r.getXpEarned(),
                      r.getStarsEarned()));
              text(
                  R.id.exUnlocks,
                  r.getUnlocked().isEmpty()
                      ? getString(R.string.ex_no_unlocks)
                      : getString(R.string.ex_unlocks, unlockedNames(r.getUnlocked())));
              text(R.id.exRewardXp,getString(R.string.gm_xp_earned,r.getXpEarned()));
              text(R.id.exRewardStars,RewardCelebrationDialog.stars(r.getStarsEarned())+"  "+getString(R.string.gm_stars,r.getStarsEarned()));
              progress(profile.getResult(), week.getResult());
              loaded();
              if(saved.getResult().exists()&&!saved.getResult().getMetadata().hasPendingWrites()&&!saved.getResult().getMetadata().isFromCache()){
                celebration=r;celebrationSession=id;celebrationOwner=account;maybeCelebrate();
              }
            })
        .addOnFailureListener(this, this::error);
  }

  @Override protected void onResume(){super.onResume();maybeCelebrate();}
  @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("celebrationHandled",celebrationHandled);}
  @Override protected void onStop(){if(celebrationDialog!=null){celebrationDialog.dismiss();celebrationDialog=null;}super.onStop();}
  private void maybeCelebrate(){
    if(celebration==null||uid==null||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)||isFinishing()||isDestroyed())return;
    com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();if(user==null||!uid.equals(user.getUid())||!uid.equals(celebrationOwner))return;
    if(!com.example.fitnessbalance.service.RewardCelebrationPolicy.canPresent(getIntent().getBooleanExtra(IntentKeys.CELEBRATE,false),true,celebrationHandled))return;
    celebrationHandled=true;
    if(com.example.fitnessbalance.repository.RewardCelebrationStore.claim(this,uid,celebrationSession)){
      String unlocks=celebration.getUnlocked().isEmpty()?"":getString(R.string.ex_unlocks,unlockedNames(celebration.getUnlocked()));
      celebrationDialog=RewardCelebrationDialog.show(this,celebration,unlocks);
    }
  }
}
