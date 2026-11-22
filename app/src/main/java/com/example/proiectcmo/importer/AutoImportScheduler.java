package com.example.proiectcmo.importer;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.proiectcmo.repository.ImportStateRepository;
import com.google.firebase.auth.FirebaseAuth;

public final class AutoImportScheduler {
    public static final String WORK_NAME = "auto_import";

    private static final String TAG = "AutoImportScheduler";

    private AutoImportScheduler() {
    }

    public static void maybeSchedule(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        Context app = context.getApplicationContext();
        new ImportStateRepository().getSync()
                .addOnSuccessListener(sync -> {
                    if (!AutoImportPolicy.shouldRun(sync.lastAutoCheck, System.currentTimeMillis())) {
                        return;
                    }
                    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AutoImportWorker.class)
                            .setConstraints(new Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build())
                            .build();
                    WorkManager.getInstance(app)
                            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Starea sincronizării nu a putut fi citită", e));
    }
}
