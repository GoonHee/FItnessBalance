package com.example.fitnessbalance.repository;

import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** The dated document is both the earned chest and the permanent cosmetic unlock receipt. */
public final class DailyChestRepository {
  public enum Action { CHECK, REST, REFLECT, OPEN }

  public static final class Chest {
    public final boolean movement, meal, reflection, restAvailable, earned, opened;
    public final String accent;

    Chest(boolean movement, boolean meal, boolean reflection, boolean restAvailable,
        boolean earned, boolean opened, String accent) {
      this.movement = movement;
      this.meal = meal;
      this.reflection = reflection;
      this.restAvailable = restAvailable;
      this.earned = earned;
      this.opened = opened;
      this.accent = accent;
    }
  }

  private final FirebaseFirestore db;
  private final DocumentReference user;

  public DailyChestRepository(String uid) {
    db = FirebaseFirestore.getInstance();
    user = db.collection("users").document(uid);
  }

  public Task<Chest> update(LocalDate date, Action action) {
    LocalDate currentDate = LocalDate.now();
    if (date.isAfter(currentDate)) return Tasks.forException(new IllegalArgumentException("Future chest date"));
    boolean today = date.equals(currentDate);
    String day = date.toString();
    DocumentReference receipt = user.collection("dailyChests").document(day);
    Task<Chest> update = db.runTransaction(tx -> {
      DocumentSnapshot saved = tx.get(receipt);
      String storedAccent = saved.getString("accent");
      String accent = storedAccent == null ? accent(date) : storedAccent;
      if (flag(saved, "opened")) return opened(accent);

      DocumentSnapshot workout = tx.get(user.collection("questProgress").document(day + "_planned"));
      DocumentSnapshot feedback = tx.get(user.collection("questProgress").document(day + "_feedback"));
      DocumentSnapshot meals = tx.get(user.collection("foodLogs").document(day));
      DocumentSnapshot profile = tx.get(user);
      boolean plannedRest = today && plannedRest(profile.get("preferredWorkoutDays"), date);
      boolean rest = flag(saved, "rest") || (action == Action.REST && plannedRest);
      boolean reflected = flag(saved, "reflection") || (today && action == Action.REFLECT);
      Object items = meals.get("foodItems");
      Object legacyCalories = meals.get("totalCalories");
      boolean meal = items instanceof List ? !((List<?>) items).isEmpty()
          : !meals.contains("foodItems") && legacyCalories instanceof Number
              && ((Number) legacyCalories).doubleValue() > 0;
      boolean moved = rest || flag(workout, "completed");
      boolean reflection = reflected || flag(feedback, "completed");
      boolean earned = flag(saved, "earned") || qualifies(moved, meal, reflection);
      boolean opened = action == Action.OPEN && earned;
      if (opened || rest != flag(saved, "rest") || reflected != flag(saved, "reflection")
          || earned != flag(saved, "earned")) {
        tx.set(receipt, Values.of("rest", rest, "reflection", reflected, "earned", earned,
            "opened", opened, "accent", accent, "updatedAt", FieldValue.serverTimestamp()));
      }
      return new Chest(moved, meal, reflection, plannedRest && !moved, earned, opened, accent);
    });
    if (action != Action.OPEN) return update;
    return update.continueWithTask(outcome -> {
      if (outcome.isSuccessful()) return Tasks.forResult(outcome.getResult());
      // A concurrent open can make a pending transaction fail the immutable receipt rule.
      return receipt.get(Source.SERVER).continueWith(confirmed -> {
        if (confirmed.isSuccessful() && flag(confirmed.getResult(), "opened")) {
          return opened(confirmed.getResult().getString("accent"));
        }
        throw outcome.getException() == null
            ? new IllegalStateException("Chest open failed") : outcome.getException();
      });
    });
  }

  private static Chest opened(String accent) {
    return new Chest(false, false, false, false, true, true, accent);
  }

  public static boolean qualifies(boolean movement, boolean meal, boolean reflection) {
    return (movement && (meal || reflection)) || (meal && reflection);
  }

  public static String accent(LocalDate date) {
    return new String[]{"teal", "purple", "amber"}[(int) Math.floorMod(date.toEpochDay(), 3L)];
  }

  static boolean plannedRest(Object days, LocalDate date) {
    List<?> schedule = days instanceof List ? (List<?>) days : Collections.emptyList();
    if (schedule.isEmpty()) return false;
    for (Object day : schedule) {
      if (Values.integer(day, -1) == date.getDayOfWeek().getValue()) return false;
    }
    return true;
  }

  private static boolean flag(DocumentSnapshot row, String name) {
    return Boolean.TRUE.equals(row.getBoolean(name));
  }
}
