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

  /** Explicit confirmation only. A permanent receipt prevents cross-device/retry duplication. */
  public Task<ActivityDay> confirmWearable(Map<String,Object> plan,Map<String,Object> session) {
    final Map<String,Object> entry;final String day,key;
    try {
      entry=com.example.fitnessbalance.health.WearableWorkoutLinkService.entry(plan,session,java.time.ZoneId.systemDefault(),System.currentTimeMillis());
      day=(String)entry.get("localDay");key=com.example.fitnessbalance.health.WearableWorkoutLinkService.key(session);validateDay(day);
    }catch(RuntimeException e){return Tasks.forException(e);}
    // A guided session can end after midnight. Read a full possible guided duration beyond
    // this session, then compare actual intervals inside the confirmation transaction.
    long from=((Number)entry.get("startedAtMillis")).longValue(),to=((Number)entry.get("endedAtMillis")).longValue();
    return user.collection(RECORDS).whereGreaterThanOrEqualTo("completedAt",new Date(from))
      .whereLessThanOrEqualTo("completedAt",new Date(to+86400000L)).limit(501).get(Source.SERVER).continueWithTask(task->{
        if(!task.isSuccessful())return Tasks.forException(task.getException());
        if(task.getResult().size()>500)return Tasks.forException(new IllegalStateException("Too many workouts to check. Confirmation stopped."));
        for(DocumentSnapshot doc:task.getResult()) {
          com.google.firebase.Timestamp a=doc.getTimestamp("startedAt"),b=doc.getTimestamp("completedAt");
          if(a==null||b==null)return Tasks.forException(new IllegalStateException("A guided workout has incomplete timing. Review its log before importing."));
          if(com.example.fitnessbalance.health.WearableWorkoutService.overlaps(from,to,a.toDate().getTime(),b.toDate().getTime()))
            return Tasks.forException(new IllegalStateException("This overlaps a counted guided workout. It cannot be counted again."));
        }
        return guidedRecords(day).continueWithTask(records->{if(!records.isSuccessful())return Tasks.forException(records.getException());return write(day,records.getResult(),key,0,entry,true,false,key);});
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
    return write(day,guided,id,revision,entry,add,remove,null);
  }

  private Task<ActivityDay> write(String day,List<Map<String,Object>> guided,String id,int revision,
      Map<String,Object> entry,boolean add,boolean remove,String wearableKey) {
    Task<ActivityDay> first=writeAttempt(day,guided,id,revision,entry,add,remove,wearableKey);
    if(wearableKey==null)return first;
    return first.continueWithTask(outcome->{
      if(outcome.isSuccessful())return Tasks.forResult(outcome.getResult());
      Exception error=outcome.getException();
      if(!(error instanceof FirebaseFirestoreException)||!Arrays.asList(FirebaseFirestoreException.Code.PERMISSION_DENIED,FirebaseFirestoreException.Code.ABORTED,FirebaseFirestoreException.Code.UNAVAILABLE).contains(((FirebaseFirestoreException)error).getCode()))return Tasks.forException(error);
      return user.collection("wearableWorkoutLinks").document(wearableKey).get(Source.SERVER).continueWithTask(saved->{
        if(saved.isSuccessful()&&saved.getResult().exists())return loadDay(day).continueWithTask(loaded->{
          if(!loaded.isSuccessful())return Tasks.forException(loaded.getException());
          return new WearableGamificationRepository().claim(user.getId(),day,wearableKey).continueWith(claimed->{
            if(!claimed.isSuccessful())throw claimed.getException();ActivityDay value=loaded.getResult();return new ActivityDay(day,value.manual,value.guided,value.totals,value.caloriesConsumed,claimed.getResult());
          });
        });
        // An immutable date-lock rule can reject the losing concurrent writer before the SDK
        // retries. Re-read all transaction state once; never bypass the award/date receipts.
        return writeAttempt(day,guided,id,revision,entry,add,remove,wearableKey);
      });
    });
  }

  private Task<ActivityDay> writeAttempt(String day,List<Map<String,Object>> guided,String id,int revision,
      Map<String,Object> entry,boolean add,boolean remove,String wearableKey) {
    DocumentReference manualRef = user.collection(MANUAL_LOGS).document(day),
        dailyRef = user.collection(DAILY_DATA).document(day);
    return db.runTransaction(
        tx -> {
          DocumentReference receipt=wearableKey==null?null:user.collection("wearableWorkoutLinks").document(wearableKey);
          if(receipt!=null&&tx.get(receipt).exists())throw new IllegalStateException("This wearable session was already recorded. Check the activity log; it will not be counted again.");
          DocumentSnapshot manual = tx.get(manualRef), daily = tx.get(dailyRef);
          // Serialise against guided completions, which also update this daily document.
          if(receipt!=null&&daily.contains("lastGuidedCompletedAt")) {
            Long completed=daily.getLong("lastGuidedCompletedAt");
            if(completed!=null&&completed>=((Number)entry.get("startedAtMillis")).longValue())
              throw new IllegalStateException("A guided workout was completed on this date. Review its log before importing.");
          }
          List<Map<String, Object>> entries =
              service.manualEntries(
                  day,
                  manual.getData() == null ? Collections.emptyMap() : manual.getData(),
                  daily.getData() == null ? Collections.emptyMap() : daily.getData());
          if(receipt!=null)for(Map<String,Object> row:entries){
            if("health_connect".equals(row.get("source"))&&Boolean.TRUE.equals(row.get("completed"))
                && row.get("startedAtMillis") instanceof Number&&row.get("endedAtMillis") instanceof Number
                && com.example.fitnessbalance.health.WearableWorkoutService.overlaps(((Number)entry.get("startedAtMillis")).longValue(),((Number)entry.get("endedAtMillis")).longValue(),((Number)row.get("startedAtMillis")).longValue(),((Number)row.get("endedAtMillis")).longValue()))
              throw new IllegalStateException("An overlapping wearable workout is already counted. Review the activity log.");
          }
          if (id != null) entries = service.mutate(entries, id, revision, entry, add, remove);
          Map<String, Object> contributions =
              service.contributions(daily.get("structuredExerciseContributions"));
          // Guided records are immutable and were fetched from the server. Merge by record ID,
          // preserving contributions committed after the queries started.
          for (Map<String, Object> record : guided)
            service.guided(contributions, (String) record.get("recordId"), record);
          WearableGamificationRepository.Award award=receipt==null?null:WearableGamificationRepository.prepare(tx,user,id,entry,System.currentTimeMillis());
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
          if(receipt!=null)tx.set(receipt,Values.of("date",day,"entryId",id,"createdAt",FieldValue.serverTimestamp()));
          if(award!=null)award.apply(tx);
          return new ActivityDay(
              day, entries, guided, totals, Values.integer(daily.get("caloriesConsumed"), 0),award==null?Collections.emptyMap():award.result);
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
      int activeSeconds,
      long startedAtMillis) {
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
    // If an import wins the race with a guided completion, retain the guided result/rewards
    // but suppress its second daily calorie/time contribution.
    for(Map<String,Object> row:entries)if("health_connect".equals(row.get("source"))&&Boolean.TRUE.equals(row.get("completed"))
        && row.get("startedAtMillis") instanceof Number&&row.get("endedAtMillis") instanceof Number
        && com.example.fitnessbalance.health.WearableWorkoutService.overlaps(startedAtMillis,System.currentTimeMillis(),((Number)row.get("startedAtMillis")).longValue(),((Number)row.get("endedAtMillis")).longValue()))
      contributions.put(session,Values.of("calories",0,"activeSeconds",0,"suppressedByWearable",true,"wearableEntryId",row.get("entryId"),"originalCalories",calories,"originalActiveSeconds",activeSeconds));
    Map<String, Object> update = service.totals(entries, contributions);
    update.put("structuredExerciseContributions", contributions);
    update.put("lastGuidedCompletedAt",System.currentTimeMillis());
    update.put("lastUpdated", FieldValue.serverTimestamp());
    tx.set(dailyRef, update, SetOptions.mergeFields(update.keySet().toArray(new String[0])));
  }
}
