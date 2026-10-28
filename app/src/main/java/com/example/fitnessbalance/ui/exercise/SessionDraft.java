package com.example.fitnessbalance.ui.exercise;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONObject;

/** Private durable local draft, namespaced by authenticated UID. No health data is logged. */
public class SessionDraft {
  public String id = "", path = "", lesson = "", owner = "";
  public int index, completed, skipped, planned;
  public long remainingMs, elapsedMs, exerciseMs;
  public boolean rest, paused, feedback;
  public double calories, weight = 70;

  private static String key() {
    if (FirebaseAuth.getInstance().getCurrentUser() == null) return "signed_out";
    return FirebaseAuth.getInstance().getCurrentUser().getUid();
  }

  public void save(Context c) {
    if (owner.isEmpty()) owner = key();
    if (!owner.equals(key())) return;
    try {
      JSONObject j = new JSONObject();
      j.put("id", id)
          .put("path", path)
          .put("lesson", lesson)
          .put("index", index)
          .put("completed", completed)
          .put("skipped", skipped)
          .put("planned", planned)
          .put("remaining", remainingMs)
          .put("elapsed", elapsedMs)
          .put("exercise", exerciseMs)
          .put("rest", rest)
          .put("paused", paused)
          .put("feedback", feedback)
          .put("calories", calories)
          .put("weight", weight);
      c.getSharedPreferences("exercise_drafts", Context.MODE_PRIVATE)
          .edit()
          .putString(key(), j.toString())
          .commit();
    } catch (org.json.JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  public static SessionDraft read(Context c) {
    String raw =
        c.getSharedPreferences("exercise_drafts", Context.MODE_PRIVATE).getString(key(), null);
    if (raw == null) return null;
    try {
      JSONObject j = new JSONObject(raw);
      SessionDraft d = new SessionDraft();
      d.owner = key();
      d.id = j.getString("id");
      d.path = j.getString("path");
      d.lesson = j.getString("lesson");
      d.index = j.optInt("index");
      d.completed = j.optInt("completed");
      d.skipped = j.optInt("skipped");
      d.planned = j.optInt("planned");
      d.remainingMs = j.optLong("remaining");
      d.elapsedMs = j.optLong("elapsed");
      d.exerciseMs = j.optLong("exercise");
      d.rest = j.optBoolean("rest");
      d.paused = true;
      d.feedback = j.optBoolean("feedback");
      d.calories = j.optDouble("calories");
      d.weight = j.optDouble("weight", 70);
      return d;
    } catch (org.json.JSONException e) {
      return null;
    }
  }

  public static void clear(Context c, String session) {
    SessionDraft d = read(c);
    if (d != null && d.id.equals(session))
      c.getSharedPreferences("exercise_drafts", Context.MODE_PRIVATE).edit().remove(key()).commit();
  }
}
