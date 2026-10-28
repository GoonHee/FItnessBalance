package com.example.fitnessbalance;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.fitnessbalance.adapter.ActivityLogAdapter;
import com.example.fitnessbalance.model.ActivityDay;
import com.example.fitnessbalance.repository.ExerciseLogRepository;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.ui.exercise.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.DocumentSnapshot;
import java.text.*;
import java.util.*;

/** Unified activity log; old exerciseLogs documents remain the canonical manual source. */
public final class ExerciseActivity extends ExerciseBaseActivity
    implements ActivityLogAdapter.Actions {
  private String selectedDay;
  private double weight = 70;
  private ArrayList<String> preferences = new ArrayList<>();
  private ExerciseLogRepository logs;
  private int request;
  private boolean deleting;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    screen(R.layout.activity_activity_log);
    selectedDay =
        state == null
            ? DateUtils.day(new Date(), TimeZone.getDefault())
            : state.getString("selectedDay", DateUtils.day(new Date(), TimeZone.getDefault()));
    findViewById(R.id.alDate).setOnClickListener(v -> chooseDate());
    findViewById(R.id.alAdd).setOnClickListener(v -> editor(null));
    getSupportFragmentManager()
        .setFragmentResultListener(
            ManualActivityDialog.RESULT,
            this,
            (key, result) -> {
              if (selectedDay.equals(result.getString("day"))) reload();
            });
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putString("selectedDay", selectedDay);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void load() {
    deleting = false;
    enabled(R.id.alDate, true);
    loading();
    enabled(R.id.alAdd, false);
    text(R.id.alDate, getString(R.string.al_day, selectedDay));
    text(R.id.alTotal, "");
    text(R.id.alBreakdown, "");
    text(R.id.alTime, "");
    list(
        R.id.alHistory,
        new ActivityLogAdapter(
            new ActivityDay(
                selectedDay,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                0),
            this));
    logs = new ExerciseLogRepository(uid);
    String day = selectedDay;
    int token = ++request;
    Task<ActivityDay> activities = logs.loadDay(day);
    Task<DocumentSnapshot> profile = repository.profile();
    Tasks.whenAllComplete(activities, profile)
        .addOnSuccessListener(
            this,
            unused -> {
              if (token != request || !day.equals(selectedDay)) return;
              if (!activities.isSuccessful()) {
                error(activities.getException());
                return;
              }
              weight = 70;
              preferences.clear();
              if (profile.isSuccessful()) {
                weight =
                    new ExerciseCalorieService()
                        .weight(Values.decimal(profile.getResult().get("weight"), 70));
                preferences.addAll(Values.strings(profile.getResult().get("preferredExercises")));
              }
              render(activities.getResult());
            })
        .addOnFailureListener(this, this::error);
  }

  private void render(ActivityDay day) {
    text(
        R.id.alTotal,
        getString(R.string.al_total_value, Values.integer(day.totals.get("caloriesBurned"), 0)));
    text(
        R.id.alBreakdown,
        getString(
            R.string.al_breakdown,
            Values.integer(day.totals.get("manualCaloriesBurned"), 0),
            Values.integer(day.totals.get("structuredCaloriesBurned"), 0)));
    text(
        R.id.alTime,
        getString(
            R.string.al_time,
            Values.decimal(day.totals.get("exerciseActiveSeconds"), 0) / 60,
            Values.integer(day.totals.get("exerciseEntryCount"), 0)));
    list(R.id.alHistory, new ActivityLogAdapter(day, this));
    enabled(R.id.alAdd, !deleting);
    enabled(R.id.alDate, !deleting);
    loaded();
    if (day.manual.isEmpty() && day.guided.isEmpty()) status(R.string.al_empty);
  }

  private void chooseDate() {
    if (deleting) return;
    Calendar c = Calendar.getInstance();
    try {
      c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(selectedDay));
    } catch (ParseException ignored) {
    }
    DatePickerDialog dialog =
        new DatePickerDialog(
            this,
            (view, y, m, d) -> {
              Calendar picked = Calendar.getInstance();
              picked.set(y, m, d);
              selectedDay = DateUtils.day(picked.getTime(), TimeZone.getDefault());
              reload();
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH));
    dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
    dialog.show();
  }

  private void editor(Map<String, Object> item) {
    if (deleting || getSupportFragmentManager().findFragmentByTag("manual_activity") != null)
      return;
    ManualActivityDialog.create(uid, selectedDay, weight, preferences, item)
        .show(getSupportFragmentManager(), "manual_activity");
  }

  @Override
  public void edit(Map<String, Object> item) {
    editor(item);
  }

  @Override
  public void delete(Map<String, Object> item) {
    if (deleting) return;
    String day = selectedDay;
    new AlertDialog.Builder(this)
        .setMessage(R.string.al_delete_confirm)
        .setPositiveButton(
            R.string.al_delete,
            (d, w) -> {
              deleting = true;
              enabled(R.id.alAdd, false);
              enabled(R.id.alDate, false);
              status(R.string.al_delete_busy);
              logs.delete(
                      day,
                      Values.text(item.get("entryId"), ""),
                      Values.integer(item.get("revision"), 0))
                  .addOnSuccessListener(
                      this,
                      result -> {
                        deleting = false;
                        Toast.makeText(this, R.string.al_removed, Toast.LENGTH_SHORT).show();
                        if (selectedDay.equals(day)) render(result);
                      })
                  .addOnFailureListener(
                      this,
                      e -> {
                        deleting = false;
                        enabled(R.id.alDate, true);
                        error(e);
                      });
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  @Override
  public void view(String id) {
    if (!deleting) openSession(WorkoutResultActivity.class, id);
  }

  @Override
  protected void error(Exception e) {
    super.error(e);
    if (e instanceof ExerciseLogService.Conflict) status(R.string.al_conflict);
    else if (!(e instanceof com.google.firebase.firestore.FirebaseFirestoreException))
      status(R.string.al_error);
  }
}
