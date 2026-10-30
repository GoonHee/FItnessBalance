package com.example.fitnessbalance;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.time.LocalDate;
import java.util.*;

final class FoodRecommendationRepository {
    static final class Context {
        final Map<String,Object> profile; final List<Map<String,Object>> logged,foods;
        final Set<String> recent;
        Context(Map<String,Object> p,List<Map<String,Object>> l,List<Map<String,Object>> f,Set<String> r) { profile=p;logged=l;foods=f;recent=r; }
    }
    Task<Context> load(String uid,String day) {
        if(!FoodUi.validDate(day) || LocalDate.parse(day).isAfter(LocalDate.now())) return Tasks.forException(new IllegalArgumentException("Invalid recommendation date"));
        FirebaseFirestore db=FirebaseFirestore.getInstance(); DocumentReference user=db.collection("users").document(uid);
        Task<DocumentSnapshot> profile=user.get(Source.SERVER),log=user.collection("foodLogs").document(day).get(Source.SERVER);
        Task<QuerySnapshot> foods=db.collection("foods").whereEqualTo("recommendation_ready",true).limit(101).get(Source.SERVER);
        Task<QuerySnapshot> recent=user.collection("foodLogs")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(),LocalDate.parse(day).minusDays(6).toString())
                .whereLessThanOrEqualTo(FieldPath.documentId(),day).get(Source.SERVER);
        return Tasks.whenAll(profile,log,foods,recent).continueWith(done -> {
            if(!done.isSuccessful()) throw done.getException();
            FirebaseUser active=FirebaseAuth.getInstance().getCurrentUser();
            if(active==null || !uid.equals(active.getUid())) throw new IllegalStateException("Account changed");
            if(!profile.getResult().exists()) throw new IllegalStateException("Missing profile");
            if(foods.getResult().size()>100) throw new IllegalStateException("Recommendation catalogue exceeds reviewed pool limit");
            List<Map<String,Object>> candidates=new ArrayList<>();
            for(DocumentSnapshot doc:foods.getResult()) {
                Map<String,Object> data=new HashMap<>(doc.getData()); data.put("food_key",doc.getId()); candidates.add(data);
            }
            Set<String> ids=new HashSet<>();
            for(DocumentSnapshot document:recent.getResult()) for(Map<String,Object> entry:FoodLogRepository.entries(document)) {
                Object id=entry.get("foodId"); if(id instanceof String) ids.add((String)id);
            }
            return new Context(profile.getResult().getData(),FoodLogRepository.entries(log.getResult()),candidates,ids);
        });
    }
}
