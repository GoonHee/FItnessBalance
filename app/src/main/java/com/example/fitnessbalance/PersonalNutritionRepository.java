package com.example.fitnessbalance;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
import java.util.*;
final class PersonalNutritionRepository {
    private final DocumentReference ref;
    PersonalNutritionRepository(String uid,String foodId){ref=FirebaseFirestore.getInstance().collection("users").document(uid).collection("foodNutrition").document(foodId);}
    Task<DocumentSnapshot> get(){return ref.get(Source.SERVER);}
    Task<Void> save(Map<String,Object> raw,long revision,String mutation){Map<String,Object> data=new HashMap<>(raw);return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);
        if(old.exists()&&mutation.equals(old.getString("mutationId"))){
            for(String key:data.keySet())if(!Objects.equals(old.get(key),data.get(key)))throw new IllegalStateException("A previous save completed. Reopen before changing its values.");return null;
        }
        if(revision==0?old.exists():!old.exists()||old.getLong("revision")==null||old.getLong("revision")!=revision)throw new IllegalStateException("Personal nutrition changed. Reopen this screen.");
        data.put("revision",revision+1);data.put("mutationId",mutation);data.put("updatedAt",FieldValue.serverTimestamp());data.put("confirmedAt",FieldValue.serverTimestamp());tx.set(ref,data);return null;});}
    Task<Void> delete(long revision){return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);if(!old.exists())return null;if(!Objects.equals(old.getLong("revision"),revision))throw new IllegalStateException("Personal nutrition changed. Reopen this screen.");tx.delete(ref);return null;});}
}
