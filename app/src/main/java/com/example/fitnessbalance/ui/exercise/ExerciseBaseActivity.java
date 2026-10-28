package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import com.example.fitnessbalance.LoginActivity;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.repository.*;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

public abstract class ExerciseBaseActivity extends AppCompatActivity {
  protected WorkoutRepository repository;
  protected WorkoutDraftStore drafts;
  protected String uid;
  private boolean redirecting;
  private final FirebaseAuth.AuthStateListener authListener =
      auth -> {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null || !u.getUid().equals(uid)) signedOut();
      };

  protected void screen(int layout) {
    setContentView(layout);
    repository = new WorkoutRepository();
    drafts = new WorkoutDraftStore(this);
    ((MaterialToolbar) findViewById(R.id.exToolbar))
        .setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    findViewById(R.id.exRetry).setOnClickListener(v -> reload());
    findViewById(R.id.exRetry).setVisibility(View.GONE);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.exRoot),
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
    if (u == null) {
      signedOut();
      return;
    }
    uid = u.getUid();
    FirebaseAuth.getInstance().addAuthStateListener(authListener);
    reload();
  }

  @Override
  protected void onStop() {
    FirebaseAuth.getInstance().removeAuthStateListener(authListener);
    super.onStop();
  }

  private void signedOut() {
    if (redirecting) return;
    redirecting = true;
    Toast.makeText(this, R.string.ex_sign_in, Toast.LENGTH_LONG).show();
    startActivity(new Intent(this, LoginActivity.class));
    finish();
  }

  protected void reload() {
    try {
      load();
    } catch (RuntimeException e) {
      error(e);
    }
  }

  protected abstract void load();

  protected void loading() {
    findViewById(R.id.exLoading).setVisibility(View.VISIBLE);
    findViewById(R.id.exRetry).setVisibility(View.GONE);
    findViewById(R.id.exStatus).setVisibility(View.GONE);
  }

  protected void loaded() {
    findViewById(R.id.exLoading).setVisibility(View.GONE);
    findViewById(R.id.exRetry).setVisibility(View.GONE);
    findViewById(R.id.exStatus).setVisibility(View.GONE);
  }

  protected void status(int string) {
    text(R.id.exStatus, getString(string));
    findViewById(R.id.exStatus).setVisibility(View.VISIBLE);
  }

  protected void error(Exception e) {
    android.util.Log.w("ExerciseModule", e.getClass().getSimpleName());
    findViewById(R.id.exLoading).setVisibility(View.GONE);
    findViewById(R.id.exRetry).setVisibility(View.VISIBLE);
    int message = e instanceof IllegalStateException ? R.string.ex_empty : R.string.ex_load_error;
    if (e instanceof FirebaseFirestoreException) {
      FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
      if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        message = R.string.ex_permission;
      if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
        signedOut();
        return;
      }
    }
    status(message);
  }

  protected void text(int id, CharSequence text) {
    ((TextView) findViewById(id)).setText(text);
  }

  protected void enabled(int id, boolean value) {
    findViewById(id).setEnabled(value);
  }

  protected void list(int id, RecyclerView.Adapter<?> adapter) {
    RecyclerView list = findViewById(id);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setNestedScrollingEnabled(false);
    list.setAdapter(adapter);
  }

  protected String path() {
    String value = getIntent().getStringExtra(IntentKeys.PATH);
    return value == null ? FirestoreCollections.FOUNDATION : value;
  }

  protected void open(Class<?> target, String lesson) {
    Intent intent = new Intent(this, target).putExtra(IntentKeys.PATH, path());
    if (lesson != null) intent.putExtra(IntentKeys.LESSON, lesson);
    startActivity(intent);
  }

  protected void openSession(Class<?> target, String session) {
    startActivity(new Intent(this, target).putExtra(IntentKeys.SESSION, session));
  }

  protected void progress(DocumentSnapshot profile, DocumentSnapshot week) {
    GamificationService game = new GamificationService();
    MomentumService momentum = new MomentumService();
    int xp = Values.integer(profile.get("totalXp"), 0),
        target = momentum.target(Values.integer(profile.get("weeklyWorkoutTarget"), 3));
    String key = DateUtils.week(new java.util.Date(), java.util.TimeZone.getDefault());
    int count =
        Values.integer(
            week.get("completed"),
            Values.integer(Values.map(profile.get("weeklyCounts")).get(key), 0));
    text(R.id.exLevel, getString(R.string.ex_level, game.level(xp)));
    text(R.id.exXp, getString(R.string.ex_xp, xp, game.nextLevelXp(xp)));
    ((LinearProgressIndicator) findViewById(R.id.exXpBar))
        .setProgressCompat(Math.max(0, xp) % 100, false);
    text(R.id.exMomentum, getString(R.string.ex_week_count, count, target));
    ((LinearProgressIndicator) findViewById(R.id.exMomentumBar))
        .setProgressCompat(momentum.percentage(count, target), false);
    if (findViewById(R.id.exBand) != null) {
      int[] bands = {
        R.string.ex_band_begin,
        R.string.ex_band_restart,
        R.string.ex_band_building,
        R.string.ex_band_strong
      };
      text(R.id.exBand, getString(bands[momentum.band(momentum.percentage(count, target))]));
    }
  }

  protected String unlockedNames(java.util.List<String> ids) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (String id : ids) {
      int label;
      switch (id) {
        case "lesson_02":
          label = R.string.ex_lesson_02;
          break;
        case "lesson_03":
          label = R.string.ex_lesson_03;
          break;
        case "lesson_04":
          label = R.string.ex_lesson_04;
          break;
        case "lesson_05":
          label = R.string.ex_lesson_05;
          break;
        case "lesson_06":
          label = R.string.ex_lesson_06;
          break;
        case "lesson_07":
          label = R.string.ex_lesson_07;
          break;
        case "first_step":
          label = R.string.ex_first_step;
          break;
        case "routine_builder":
          label = R.string.ex_routine_builder;
          break;
        case "path_explorer":
          label = R.string.ex_path_explorer;
          break;
        case "welcome_back":
          label = R.string.ex_welcome_back;
          break;
        case "honest_reflection":
          label = R.string.ex_honest_reflection;
          break;
        case "balanced_week":
          label = R.string.ex_balanced_week;
          break;
        default:
          continue;
      }
      names.add(getString(label));
    }
    return String.join(", ", names);
  }
}
