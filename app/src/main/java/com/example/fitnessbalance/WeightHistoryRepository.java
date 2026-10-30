package com.example.fitnessbalance;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.time.LocalDate;
import java.util.*;

final class WeightHistoryRepository {
    private final String uid;private final FirebaseFirestore db=FirebaseFirestore.getInstance();
    WeightHistoryRepository(String uid){this.uid=uid;}
    private DocumentReference user(){return db.collection("users").document(uid);}
    private DocumentReference state(){return user().collection("weightHistory").document("state");}
    Task<TreeMap<String,WeightHistoryService.Entry>> load() {
        return state().get(Source.SERVER).continueWithTask(task->{
            if(!task.isSuccessful())return Tasks.forException(task.getException());
            if(!same())return Tasks.forException(new IllegalStateException("Account changed"));
            if(task.getResult().exists()) {
                try{return Tasks.forResult(WeightHistoryService.parse(task.getResult().get("entries")));}
                catch(RuntimeException invalid){return Tasks.forException(invalid);}
            }
            return user().collection("weightLogs").limit(WeightHistoryService.LIMIT+1).get(Source.SERVER).continueWith(records->{
                if(!records.isSuccessful())throw records.getException();if(!same())throw new IllegalStateException("Account changed");
                if(records.getResult().size()>WeightHistoryService.LIMIT)throw new IllegalStateException("Weight history limit");
                TreeMap<String,WeightHistoryService.Entry> result=new TreeMap<>();
                for(DocumentSnapshot doc:records.getResult())result.put(doc.getId(),WeightHistoryService.entry(doc.getId(),doc.getData()));return result;
            });
        });
    }
    Task<Void> save(String day,long expected,double kg,boolean remove,boolean updateProfile,String mutation) {
        try { LocalDate date=LocalDate.parse(day);if(!remove){new WeightHistoryService.Entry(day,kg,Math.max(1,expected+1));if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Future measurement");} }
        catch(RuntimeException invalid){return Tasks.forException(invalid);}
        return load().continueWithTask(loaded->{
            if(!loaded.isSuccessful())return Tasks.forException(loaded.getException());
            return db.runTransaction(tx->{
                if(!same())throw new IllegalStateException("Account changed");
                DocumentReference record=user().collection("weightLogs").document(day);
                DocumentSnapshot index=tx.get(state()),existing=tx.get(record),profile=tx.get(user());
                if(!profile.exists())throw new IllegalStateException("Missing profile");
                TreeMap<String,WeightHistoryService.Entry> before=index.exists()?WeightHistoryService.parse(index.get("entries")):loaded.getResult();
                if(!remove&&existing.exists()&&mutation.equals(existing.getString("mutationId"))) {
                    Double prior=FoodNutrition.number(existing.get("weightKg"));
                    if(prior!=null&&Double.compare(prior,kg)==0&&Boolean.valueOf(updateProfile).equals(existing.getBoolean("updateProfile")))return null;
                    throw new IllegalStateException("Changed retry payload");
                }
                WeightHistoryService.Entry old=before.get(day);
                if(existing.exists()) {
                    WeightHistoryService.Entry actual=WeightHistoryService.entry(day,existing.getData());
                    if(old==null||actual.revision!=old.revision||Double.compare(actual.kg,old.kg)!=0)throw new IllegalStateException("Weight index mismatch");
                } else if(old!=null)throw new IllegalStateException("Weight index mismatch");
                TreeMap<String,WeightHistoryService.Entry> after=WeightHistoryService.mutate(before,day,expected,kg,remove,LocalDate.now());
                Map<String,Object> stateData=new HashMap<>();stateData.put("entries",WeightHistoryService.values(after));stateData.put("updatedAt",FieldValue.serverTimestamp());
                tx.set(state(),stateData);
                if(remove) {if(existing.exists())tx.delete(record);}
                else {
                    Map<String,Object> values=after.get(day).value();values.put("day",day);values.put("source","manual");
                    values.put("mutationId",mutation);values.put("updateProfile",updateProfile);values.put("updatedAt",FieldValue.serverTimestamp());tx.set(record,values);
                    if(updateProfile) {
                        Map<String,Object> weight=new HashMap<>();weight.put("weight",kg);tx.set(user(),weight,SetOptions.merge());
                    }
                }
                return null;
            });
        });
    }
    private boolean same(){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();return u!=null&&uid.equals(u.getUid());}
}
