package com.example.fitnessbalance.repository;

import static com.example.fitnessbalance.util.FirestoreCollections.*;

import com.example.fitnessbalance.model.ActivityDay;
import com.example.fitnessbalance.service.ExerciseLogService;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.text.*;
import java.util.*;

/** Both sources update dailyData atomically, preserving food/nutrient fields. */
public final class ExerciseLogRepository {
  private final FirebaseFirestore db = FirebaseFirestore.getInstance();
  private final DocumentReference user;
  private final ExerciseLogService service = new ExerciseLogService();

  public ExerciseLogRepository(String uid) {
    if (uid == null || uid.isEmpty()) throw new IllegalArgumentException("Authentication required");
    user = db.collection(USERS).document(uid);
  }

  public static void validateDay(String day) {
    try {
      SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
      f.setLenient(false);
      Date parsed = f.parse(day);
      if (parsed == null
          || !day.equals(f.format(parsed))
          || day.compareTo(DateUtils.day(new Date(), TimeZone.getDefault())) > 0)
        throw new IllegalArgumentException("Invalid activity date");
    } catch (ParseException | NullPointerException e) {
      throw new IllegalArgumentException("Invalid activity date", e);
    }
  }

  public Task<ActivityDay> loadDay(String day) {
    try {
      validateDay(day);
    } catch (IllegalArgumentException e) {
      return Tasks.forException(e);
    }
    return guidedRecords(day)
        .continueWithTask(
            records -> {
              if (!records.isSuccessful()) return Tasks.forException(records.getException());
              return write(day, records.getResult(), null, 0, null, false, false);
            });
  }

  public Task<ActivityDay> add(String day, String id, Map<String, Object> entry) {
    return mutate(day, id, 0, entry, true, false);
  }

  public Task<ActivityDay> edit(String day, String id, int revision, Map<String, Object> entry) {
    return mutate(day, id, revision, entry, false, false);
  }

  public Task<ActivityDay> delete(String day, String id, int revision) {
    return mutate(day, id, revision, null, false, true);
  }

  private Task<ActivityDay> mutate(
      String day, String id, int revision, Map<String, Object> entry, boolean add, boolean remove) {
    try {
      validateDay(day);
      if (id == null || id.isEmpty() || id.length() > 100)
        throw new IllegalArgumentException("Invalid entry ID");
      if (!remove) validateEntry(entry);
    } catch (IllegalArgumentException e) {
      return Tasks.forException(e);
    }
    return guidedRecords(day)
        .continueWithTask(
            records -> {
              if (!records.isSuccessful()) return Tasks.forException(records.getException());
              return write(day, records.getResult(), id, revision, entry, add, remove);
            });
  }

  private void validateEntry(Map<String, Object> row) {
    if (row == null
        || Values.text(row.get("name"), "").trim().isEmpty()
        || Values.text(row.get("name"), "").length() > 100
        || !(row.get("completed") instanceof Boolean))
      throw new IllegalArgumentException("Invalid activity");
    double minutes = Values.decimal(row.get("duration"), -1),
        calories = Values.decimal(row.get("calories"), -1);
    if (minutes <= 0 || minutes > 1440 || calories < 0 || calories > 200000)
      throw new IllegalArgumentException("Invalid activity totals");
  }

  private Task<List<Map<String, Object>>> guidedRecords(String day) {
    Calendar start = Calendar.getInstance();
    try {
      SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
      f.setLenient(false);
      start.setTime(f.parse(day));
    } catch (ParseException e) {
      return Tasks.forException(e);
    }
    Calendar end = (Calendar) start.clone();
    end.add(Calendar.DATE, 1);
    CollectionReference records = user.collection(RECORDS);
    Task<QuerySnapshot> byDay = records.whereEqualTo("localDay", day).get(Source.SERVER),
        byTime =
            records
                .whereGreaterThanOrEqualTo("completedAt", start.getTime())
                .whereLessThan("completedAt", end.getTime())
                .get(Source.SERVER);
    return Tasks.whenAll(byDay, byTime)
        .continueWith(
            done -> {
              if (!done.isSuccessful()) throw done.getException();
              Map<String, Map<String, Object>> found = new LinkedHashMap<>();
              for (QuerySnapshot page : Arrays.asList(byDay.getResult(), byTime.getResult()))
                for (DocumentSnapshot doc : page) {
                  Map<String, Object> row = new HashMap<>(doc.getData());
                  String stored = Values.text(row.get("localDay"), "");
                  if (stored.isEmpty() || day.equals(stored)) {
                    row.put("recordId", doc.getId());
                    found.put(doc.getId(), row);
                  }
                }
              return new ArrayList<>(found.values());
            });
  }

  private Task<ActivityDay> write(
      String day,
      List<Map<String, Object>> guided,
      String id,
      int revision,
      Map<String, Object> entry,
      boolean add,
      boolean remove) {
    DocumentReference manualRef = user.collection(MANUAL_LOGS).document(day),
        dailyRef = user.collection(DAILY_DATA).document(day);
    return db.runTransaction(
        tx -> {
          DocumentSnapshot manual = tx.get(manualRef), daily = tx.get(dailyRef);
          List<Map<String, Object>> entries =
              service.manualEntries(
                  day,
                  manual.getData() == null ? Collections.emptyMap() : manual.getData(),
                  daily.getData() == null ? Collections.emptyMap() : daily.getData());
          if (id != null) entries = service.mutate(entries, id, revision, entry, add, remove);
          Map<String, Object> contributions =
              service.contributions(daily.get("structuredExerciseContributions"));
          // Guided records are immutable and were fetched from the server. Merge by record ID,
          // preserving contributions committed after the queries started.
          for (Map<String, Object> record : guided)
            service.guided(contributions, (String) record.get("recordId"), record);
          Map<String, Object> totals = service.totals(entries, contributions);
          if (id != null)
            tx.set(
                manualRef,
                Values.of(
                    "date",
                    day,
                    "exercises",
                    entries,
                    "totalCaloriesBurned",
                    totals.get("manualCaloriesBurned"),
                    "timestamp",
                    FieldValue.serverTimestamp()),
                SetOptions.merge());
          Map<String, Object> update = new HashMap<>(totals);
          update.put("structuredExerciseContributions", contributions);
          update.put("lastUpdated", FieldValue.serverTimestamp());
          if (id != null || manual.exists() || daily.exists() || !guided.isEmpty())
            tx.set(
                dailyRef, update, SetOptions.mergeFields(update.keySet().toArray(new String[0])));
          return new ActivityDay(
              day, entries, guided, totals, Values.integer(daily.get("caloriesConsumed"), 0));
        });
  }

  /** Called inside workout completion, after all reads and before any other mutation. */
  public static void creditGuided(
      Transaction tx,
      DocumentReference dailyRef,
      DocumentSnapshot daily,
      DocumentSnapshot manual,
      String day,
      String session,
      int calories,
      int activeSeconds) {
    ExerciseLogService service = new ExerciseLogService();
    List<Map<String, Object>> entries =
        service.manualEntries(
            day,
            manual.getData() == null ? Collections.emptyMap() : manual.getData(),
            daily.getData() == null ? Collections.emptyMap() : daily.getData());
    Map<String, Object> contributions =
        service.contributions(daily.get("structuredExerciseContributions"));
    service.guided(
        contributions,
        session,
        Values.of("estimatedCaloriesBurned", calories, "activeDurationSeconds", activeSeconds));
    Map<String, Object> update = service.totals(entries, contributions);
    update.put("structuredExerciseContributions", contributions);
    update.put("lastUpdated", FieldValue.serverTimestamp());
    tx.set(dailyRef, update, SetOptions.mergeFields(update.keySet().toArray(new String[0])));
  }
}
