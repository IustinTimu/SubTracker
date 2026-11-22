package com.example.proiectcmo;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class SubTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                BuildConfig.DEBUG
                        ? DebugAppCheckProviderFactory.getInstance()
                        : PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}
