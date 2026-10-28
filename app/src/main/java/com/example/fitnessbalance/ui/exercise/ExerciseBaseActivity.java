package com.example.fitnessbalance.ui.exercise;

import android.content.Intent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.*;
import com.example.fitnessbalance.*;
import com.example.fitnessbalance.repository.WorkoutRepository;
import com.example.fitnessbalance.util.*;
import com.google.firebase.auth.FirebaseAuth;

public abstract class ExerciseBaseActivity extends AppCompatActivity {
  protected final WorkoutRepository repo = new WorkoutRepository();
  protected boolean ready;

  protected void screen(int layout) {
    setContentView(layout);
    findViewById(R.id.exBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    findViewById(R.id.exRetry).setOnClickListener(v -> load());
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
    if (FirebaseAuth.getInstance().getCurrentUser() == null) {
      Toast.makeText(this, R.string.ex_sign_in, Toast.LENGTH_LONG).show();
      startActivity(new Intent(this, LoginActivity.class));
      finish();
    } else if (ready) load();
  }

  protected abstract void load();

  protected void loading() {
    findViewById(R.id.exLoading).setVisibility(View.VISIBLE);
    findViewById(R.id.exRetry).setVisibility(View.GONE);
    findViewById(R.id.exStatus).setVisibility(View.GONE);
    findViewById(R.id.exContent).setVisibility(View.GONE);
  }

  protected void loaded() {
    findViewById(R.id.exLoading).setVisibility(View.GONE);
    findViewById(R.id.exRetry).setVisibility(View.GONE);
    findViewById(R.id.exStatus).setVisibility(View.GONE);
    findViewById(R.id.exContent).setVisibility(View.VISIBLE);
  }

  protected void message(int id) {
    TextView t = findViewById(R.id.exStatus);
    t.setText(id);
    t.setVisibility(View.VISIBLE);
  }

  protected void error(Exception e) {
    android.util.Log.w("ExerciseModule", e.getClass().getSimpleName());
    findViewById(R.id.exLoading).setVisibility(View.GONE);
    findViewById(R.id.exRetry).setVisibility(View.VISIBLE);
    message(R.string.ex_error);
  }

  protected String path() {
    return getIntent().getStringExtra(IntentKeys.PATH) == null
        ? FirestoreCollections.FOUNDATION
        : getIntent().getStringExtra(IntentKeys.PATH);
  }

  protected String lesson() {
    return getIntent().getStringExtra(IntentKeys.LESSON);
  }

  protected void open(Class<?> target, String lesson) {
    Intent i = new Intent(this, target);
    i.putExtra(IntentKeys.PATH, path());
    if (lesson != null) i.putExtra(IntentKeys.LESSON, lesson);
    startActivity(i);
  }

  protected void list(int id, RecyclerView.Adapter<?> adapter) {
    RecyclerView v = findViewById(id);
    v.setLayoutManager(new LinearLayoutManager(this));
    v.setAdapter(adapter);
    v.setNestedScrollingEnabled(false);
  }

  protected void text(int id, CharSequence text) {
    ((TextView) findViewById(id)).setText(text);
  }
}
