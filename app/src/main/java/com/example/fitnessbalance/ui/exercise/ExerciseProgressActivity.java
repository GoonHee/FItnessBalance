package com.example.fitnessbalance.ui.exercise;

import android.os.Bundle;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.adapter.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class ExerciseProgressActivity extends ExerciseBaseActivity {
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    screen(R.layout.activity_exercise_progress);
  }

  @Override
  protected void load() {
    loading();
    Task<DocumentSnapshot> profile = repository.profile(),
        week = repository.week(DateUtils.week(new Date(), TimeZone.getDefault()));
    Task<QuerySnapshot> records = repository.recent(),wearable=repository.user().collection("wearableWorkoutRewards").orderBy("endedAtMillis",Query.Direction.DESCENDING).limit(30).get();
    Tasks.whenAll(profile, week, records,wearable)
        .addOnSuccessListener(
            this,
            v -> {
              progress(profile.getResult(), week.getResult());
              List<ExerciseRowsAdapter.Row> rows = new ArrayList<>();
              List<DocumentSnapshot> history=new ArrayList<>(records.getResult().getDocuments());history.addAll(wearable.getResult().getDocuments());
              history.sort((a,b)->Long.compare(eventTime(b),eventTime(a)));
              for(DocumentSnapshot r:history.subList(0,Math.min(30,history.size()))){
                boolean imported="health_connect".equals(r.getString("source"));String day=imported?r.getString("day"):DateUtils.day(new Date(eventTime(r)),TimeZone.getDefault());
                rows.add(new ExerciseRowsAdapter.Row(imported?getString(R.string.wr_history_title,Values.text(r.get("activity"),"Workout")):Values.text(r.get("lessonName"),""),
                    imported?getString(R.string.wr_history,Values.integer(r.get("xpEarned"),0)):getString(R.string.ex_history_meta,Values.decimal(r.get("completionPercentage"),0),Values.integer(r.get("xpEarned"),0)),day,
                    ()->{if(imported)startActivity(new android.content.Intent(this,com.example.fitnessbalance.ExerciseActivity.class).putExtra("date",day));else openSession(WorkoutResultActivity.class,r.getId());}));
              }
              list(R.id.exRecords, new ExerciseRowsAdapter(rows));
              loaded();
              if (rows.isEmpty()) status(R.string.ex_no_records);
            })
        .addOnFailureListener(this, this::error);
  }
  private static long eventTime(DocumentSnapshot doc){if("health_connect".equals(doc.getString("source")))return Values.decimal(doc.get("endedAtMillis"),0)>0?((Number)doc.get("endedAtMillis")).longValue():0;return doc.getTimestamp("completedAt")==null?0:doc.getTimestamp("completedAt").toDate().getTime();}
}
