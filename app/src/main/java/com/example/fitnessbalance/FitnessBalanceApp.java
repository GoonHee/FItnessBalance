package com.example.fitnessbalance;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class FitnessBalanceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
    }
}