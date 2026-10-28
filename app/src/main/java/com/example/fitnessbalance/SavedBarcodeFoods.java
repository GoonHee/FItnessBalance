package com.example.fitnessbalance;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public final class SavedBarcodeFoods {

    private SavedBarcodeFoods() {}

    public static DocumentReference reference(
            String uid,
            String barcode
    ) {
        if (uid == null
                || uid.isEmpty()
                || !BarcodeFood.validBarcode(barcode)) {
            throw new IllegalArgumentException(
                    "Invalid saved product reference"
            );
        }

        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("barcodeFoods")
                .document(barcode);
    }
}