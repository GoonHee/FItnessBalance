package com.example.fitnessbalance;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
import java.util.*;
final class RecipeRepository {
    private final CollectionReference recipes;
    RecipeRepository(String uid){recipes=FirebaseFirestore.getInstance().collection("users").document(uid).collection("recipes");}
    Task<DocumentSnapshot> get(String id){return recipes.document(id).get(Source.SERVER);}
    Task<Void> save(String id,long revision,String mutation,String name,List<Map<String,Object>> raw,double yield){
        String title=SavedMealService.name(name);RecipeService.positive(yield);List<Map<String,Object>> items=SavedMealService.snapshots(raw);DocumentReference ref=recipes.document(id);
        return FirebaseFirestore.getInstance().runTransaction(tx->{DocumentSnapshot old=tx.get(ref);
            if(old.exists() && mutation.equals(old.getString("mutationId"))){
                if(title.equals(old.getString("name")) && Double.compare(old.getDouble("yieldServings"),yield)==0 && items.equals(SavedMealService.items(old.get("items"))))return null;
                throw new IllegalStateException("A previous save completed. Reopen the recipe before changing it.");
            }
            if(revision==0?old.exists():!old.exists()||old.getLong("revision")==null||old.getLong("revision")!=revision)throw new IllegalStateException("Recipe changed. Reopen it before editing.");
            Map<String,Object> data=new HashMap<>();data.put("name",title);data.put("items",items);data.put("yieldServings",yield);data.put("revision",revision+1);data.put("mutationId",mutation);
            data.put("createdAt",old.exists()?old.get("createdAt"):FieldValue.serverTimestamp());data.put("updatedAt",FieldValue.serverTimestamp());tx.set(ref,data);return null;
        });
    }
}
