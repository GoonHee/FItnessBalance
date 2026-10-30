package com.example.fitnessbalance;

import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

final class FoodPreferenceDialog {
    private FoodPreferenceDialog() {}
    static void show(AppCompatActivity activity,String foodId) {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser(); if(user==null)return; String uid=user.getUid();
        DocumentReference profile=FirebaseFirestore.getInstance().collection("users").document(uid);
        profile.get(Source.SERVER).addOnSuccessListener(activity,document->{
            if(!same(uid)||activity.isFinishing()||!document.exists())return;
            final int[] selected={0};
            try {
                if(FoodRecommendationService.strictStrings(document.get("preferredFoodIds")).contains(foodId))selected[0]=1;
                if(FoodRecommendationService.strictStrings(document.get("dislikedFoodIds")).contains(foodId))selected[0]=2;
            } catch(IllegalArgumentException e) { error(activity); return; }
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.fr_food_preference)
                    .setSingleChoiceItems(R.array.fr_preference_options,selected[0],(d,index)->selected[0]=index)
                    .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.fe_save,null).create();
            dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(!same(uid)) { dialog.dismiss(); return; }
                final int choice=selected[0]; dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false); dialog.getListView().setEnabled(false); dialog.setCancelable(false);
                FirebaseFirestore.getInstance().runTransaction(tx->{
                    DocumentSnapshot current=tx.get(profile); if(!current.exists())throw new IllegalStateException("Missing profile");
                    List<String> preferred=FoodRecommendationService.strictStrings(current.get("preferredFoodIds"));
                    List<String> disliked=FoodRecommendationService.strictStrings(current.get("dislikedFoodIds"));
                    preferred.removeAll(Collections.singleton(foodId)); disliked.removeAll(Collections.singleton(foodId));
                    if(choice==1)preferred.add(foodId); if(choice==2)disliked.add(foodId);
                    if(preferred.size()>500||disliked.size()>500)throw new IllegalArgumentException("Preference limit reached");
                    Map<String,Object> update=new HashMap<>(); update.put("preferredFoodIds",preferred); update.put("dislikedFoodIds",disliked);
                    tx.set(profile,update,SetOptions.merge()); return null;
                }).addOnSuccessListener(activity,unused->{ dialog.dismiss(); Toast.makeText(activity,R.string.fr_preference_saved,Toast.LENGTH_SHORT).show(); })
                  .addOnFailureListener(activity,e->{ dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true); dialog.getListView().setEnabled(true); error(activity); });
            })); dialog.show();
        }).addOnFailureListener(activity,e->error(activity));
    }
    private static boolean same(String uid) { FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); return u!=null&&uid.equals(u.getUid()); }
    private static void error(AppCompatActivity activity) { Toast.makeText(activity,R.string.fr_preference_error,Toast.LENGTH_LONG).show(); }
}
