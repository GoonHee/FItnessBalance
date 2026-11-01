package com.example.fitnessbalance;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
import java.util.*;

final class SavedMealRepository {
    private final CollectionReference meals;
    SavedMealRepository(String uid){meals=FirebaseFirestore.getInstance().collection("users").document(uid).collection("savedMeals");}
    Task<QuerySnapshot> page(DocumentSnapshot after){Query q=meals.orderBy(FieldPath.documentId()).limit(30);if(after!=null)q=q.startAfter(after);return q.get(Source.SERVER);}
    Task<Void> create(String id,String name,List<Map<String,Object>> raw){
        String title=SavedMealService.name(name);List<Map<String,Object>> items=SavedMealService.snapshots(raw);
        DocumentReference ref=meals.document(id);
        return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);
            if(old.exists()){
                if(!title.equals(old.getString("name"))||!items.equals(SavedMealService.items(old.get("items"))))throw new IllegalStateException("Saved meal changed. Refresh the list.");
                return null;
            }
            Map<String,Object> values=new HashMap<>();values.put("name",title);values.put("items",items);values.put("revision",1);
            values.put("createdAt",FieldValue.serverTimestamp());values.put("updatedAt",FieldValue.serverTimestamp());tx.set(ref,values);return null;
        });
    }
    Task<Void> rename(String id,long revision,String name){String title=SavedMealService.name(name);DocumentReference ref=meals.document(id);
        return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);check(old,revision);
            Map<String,Object> values=new HashMap<>();values.put("name",title);values.put("revision",revision+1);values.put("updatedAt",FieldValue.serverTimestamp());tx.update(ref,values);return null;});}
    Task<Void> delete(String id,long revision){DocumentReference ref=meals.document(id);return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);if(!old.exists())return null;check(old,revision);tx.delete(ref);return null;});}
    private static void check(DocumentSnapshot old,long revision){if(!old.exists()||old.getLong("revision")==null||old.getLong("revision")!=revision)throw new IllegalStateException("Saved meal changed. Refresh the list.");}
}
