package com.example.fitnessbalance.repository;

import com.example.fitnessbalance.model.Exercise;
import com.example.fitnessbalance.util.FirestoreCollections;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;

public final class ExerciseRepository {
  public Task<Exercise> get(String id) {
    return FirebaseFirestore.getInstance()
        .collection(FirestoreCollections.EXERCISES)
        .document(id)
        .get()
        .continueWith(
            t -> {
              if (!t.isSuccessful()) throw t.getException();
              Exercise e = t.getResult().toObject(Exercise.class);
              if (e == null || !e.getActive() || e.getName().isEmpty())
                throw new IllegalStateException("Exercise content missing");
              e.setId(id);
              return e;
            });
  }
}
