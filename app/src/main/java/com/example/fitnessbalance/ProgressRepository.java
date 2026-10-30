package com.example.fitnessbalance;

import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.time.*;
import java.util.*;

final class ProgressRepository {
    Task<ProgressService.Week> load(String uid,LocalDate start) {
        FirebaseFirestore db=FirebaseFirestore.getInstance();DocumentReference user=db.collection("users").document(uid);
        LocalDate today=LocalDate.now(),end=start.plusDays(6).isAfter(today)?today:start.plusDays(6);ZoneId zone=ZoneId.systemDefault();
        Task<DocumentSnapshot> profile=user.get(Source.SERVER);
        Task<QuerySnapshot> foods=range(user.collection("foodLogs"),start,end),daily=range(user.collection("dailyData"),start,end),manual=range(user.collection("exerciseLogs"),start,end);
        Task<QuerySnapshot> byDay=user.collection("workoutRecords").whereGreaterThanOrEqualTo("localDay",start.toString()).whereLessThanOrEqualTo("localDay",end.toString()).get(Source.SERVER);
        Task<QuerySnapshot> byTime=user.collection("workoutRecords").whereGreaterThanOrEqualTo("completedAt",Date.from(start.atStartOfDay(zone).toInstant()))
                .whereLessThan("completedAt",Date.from(end.plusDays(1).atStartOfDay(zone).toInstant())).get(Source.SERVER);
        return Tasks.whenAll(profile,foods,daily,manual,byDay,byTime).continueWith(done->{
            if(!done.isSuccessful())throw done.getException();FirebaseUser active=FirebaseAuth.getInstance().getCurrentUser();
            if(active==null||!uid.equals(active.getUid()))throw new IllegalStateException("Account changed");
            Map<String,Map<String,Object>> records=new LinkedHashMap<>();
            for(QuerySnapshot page:Arrays.asList(byDay.getResult(),byTime.getResult()))for(DocumentSnapshot document:page) {
                Map<String,Object> row=new HashMap<>(document.getData());Object day=row.get("localDay");
                if(!(day instanceof String)) { Timestamp time=document.getTimestamp("completedAt");if(time==null)continue;row.put("localDay",time.toDate().toInstant().atZone(zone).toLocalDate().toString()); }
                String date=(String)row.get("localDay");if(date.compareTo(start.toString())<0||date.compareTo(end.toString())>0)continue;
                row.put("recordId",document.getId());records.put(document.getId(),row);
            }
            return new ProgressService().build(start,today,profile.getResult().getData()==null?Collections.emptyMap():profile.getResult().getData(),map(foods.getResult()),map(daily.getResult()),map(manual.getResult()),new ArrayList<>(records.values()));
        });
    }
    private Task<QuerySnapshot> range(CollectionReference collection,LocalDate start,LocalDate end) {return collection.whereGreaterThanOrEqualTo(FieldPath.documentId(),start.toString()).whereLessThanOrEqualTo(FieldPath.documentId(),end.toString()).get(Source.SERVER);}
    private Map<String,Map<String,Object>> map(QuerySnapshot snapshot) {Map<String,Map<String,Object>> result=new HashMap<>();for(DocumentSnapshot doc:snapshot)result.put(doc.getId(),new HashMap<>(doc.getData()));return result;}
}
