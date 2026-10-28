package com.example.fitnessbalance.repository;

import android.annotation.SuppressLint;
import android.content.Context;
import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.SessionState;
import org.json.*;

/** Small durable snapshot, isolated by UID and excluded from device backups. */
public final class WorkoutDraftStore {
  private final Context context;
  private static final String FILE = "exercise_session_v2";

  public WorkoutDraftStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @SuppressLint(
      "ApplySharedPref") // Commit on state transitions so a killed process cannot replay an
                         // exercise.
  public boolean save(WorkoutDraft d) {
    try {
      return context
          .getSharedPreferences(FILE, Context.MODE_PRIVATE)
          .edit()
          .putString(d.userId, encode(d).toString())
          .commit();
    } catch (JSONException e) {
      return false;
    }
  }

  @SuppressLint("ApplySharedPref")
  public void clear(String uid, String session) {
    WorkoutDraft existing = read(uid);
    if (existing != null && existing.sessionId.equals(session))
      context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(uid).commit();
  }

  public WorkoutDraft read(String uid) {
    String raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(uid, null);
    if (raw == null) return null;
    try {
      return decode(new JSONObject(raw), uid);
    } catch (JSONException | IllegalArgumentException e) {
      return null;
    }
  }

  private JSONObject encode(WorkoutDraft d) throws JSONException {
    SessionState s = d.state;
    WorkoutLesson l = d.plan.lesson;
    JSONObject j =
        new JSONObject()
            .put("session", d.sessionId)
            .put("path", d.pathId)
            .put("weight", d.weight)
            .put("calories", d.calories)
            .put("difficulty", d.difficulty)
            .put("feeling", d.feeling)
            .put("index", s.index)
            .put("completed", s.completed)
            .put("skipped", s.skipped)
            .put("remaining", s.remainingMs)
            .put("elapsed", s.elapsedMs)
            .put("active", s.activeMs)
            .put("phase", s.phase);
    JSONObject lesson = new JSONObject().put("id", l.getId()).put("name", l.getName());
    j.put("lesson", lesson);
    JSONArray items = new JSONArray();
    for (int n = 0; n < d.plan.items.size(); n++) {
      WorkoutExercise i = d.plan.items.get(n);
      Exercise e = d.plan.exercises.get(n);
      items.put(
          new JSONObject()
              .put("id", i.getId())
              .put("exerciseId", i.getExerciseId())
              .put("targetType", i.getTargetType())
              .put("target", i.getTargetValue())
              .put("rest", i.getRestAfterSeconds())
              .put("name", e.getName())
              .put("description", e.getDescription())
              .put("instructions", new JSONArray(e.getInstructions()))
              .put("met", e.getMetValue()));
    }
    return j.put("items", items);
  }

  private WorkoutDraft decode(JSONObject j, String uid) throws JSONException {
    WorkoutDraft d = new WorkoutDraft();
    d.userId = uid;
    d.sessionId = j.getString("session");
    d.pathId = j.getString("path");
    d.weight = j.optDouble("weight", 70);
    d.calories = j.optDouble("calories", 0);
    d.difficulty = j.optString("difficulty");
    d.feeling = j.optString("feeling");
    SessionState s = d.state;
    s.index = j.getInt("index");
    s.completed = j.getInt("completed");
    s.skipped = j.getInt("skipped");
    s.remainingMs = j.getLong("remaining");
    s.elapsedMs = j.getLong("elapsed");
    s.activeMs = j.getLong("active");
    s.phase = j.getString("phase");
    s.paused = true;
    JSONObject lesson = j.getJSONObject("lesson");
    d.plan.lesson = new WorkoutLesson();
    d.plan.lesson.setId(lesson.getString("id"));
    d.plan.lesson.setName(lesson.getString("name"));
    JSONArray items = j.getJSONArray("items");
    for (int n = 0; n < items.length(); n++) {
      JSONObject row = items.getJSONObject(n);
      WorkoutExercise i = new WorkoutExercise();
      i.setId(row.getString("id"));
      i.setExerciseId(row.getString("exerciseId"));
      i.setTargetType(row.getString("targetType"));
      i.setTargetValue(row.getInt("target"));
      i.setRestAfterSeconds(row.getInt("rest"));
      d.plan.items.add(i);
      Exercise e = new Exercise();
      e.setId(i.getExerciseId());
      e.setName(row.getString("name"));
      e.setDescription(row.getString("description"));
      e.setMetValue(row.getDouble("met"));
      java.util.List<String> instructions = new java.util.ArrayList<>();
      JSONArray ins = row.getJSONArray("instructions");
      for (int k = 0; k < ins.length(); k++) instructions.add(ins.getString(k));
      e.setInstructions(instructions);
      d.plan.exercises.add(e);
    }
    if (d.sessionId.isEmpty()
        || items.length() == 0
        || s.index < 0
        || s.index >= items.length()
        || s.completed < 0
        || s.skipped < 0
        || s.completed + s.skipped > items.length()
        || s.activeMs < 0
        || s.elapsedMs < s.activeMs
        || !java.util.Arrays.asList(SessionState.EXERCISE, SessionState.REST, SessionState.FEEDBACK)
            .contains(s.phase)) throw new IllegalArgumentException("Invalid draft");
    return d;
  }
}
