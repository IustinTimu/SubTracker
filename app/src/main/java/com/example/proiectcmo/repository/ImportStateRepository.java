package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proiectcmo.importer.ImportReviewJson;
import com.example.proiectcmo.model.ImportReview;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ImportStateRepository {
    private static final String TAG = "ImportStateRepository";
    public static final int DAILY_LIMIT = 5;

    private static final String USERS = "users";
    private static final String META = "meta";
    private static final String USAGE = "usage";
    private static final String SYNC = "sync";
    private static final String PENDING = "pendingReview";

    public static class SyncState {
        public long lastAutoCheck;
        public long lastScannedEmailDate;
        public boolean needsReconnect;
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @NonNull
    private DocumentReference doc(String name) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Niciun utilizator autentificat");
        }
        CollectionReference meta = firestore.collection(USERS).document(user.getUid()).collection(META);
        return meta.document(name);
    }

    public Task<Boolean> tryConsumeAnalysis() {
        DocumentReference ref = doc(USAGE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            long count = 0;
            if (snap.exists() && today.equals(snap.getString("day"))) {
                Long stored = snap.getLong("count");
                count = stored == null ? 0 : stored;
            }
            if (count >= DAILY_LIMIT) {
                return false;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("day", today);
            data.put("count", count + 1);
            transaction.set(ref, data);
            return true;
        });
    }

    public Task<SyncState> getSync() {
        return doc(SYNC).get().continueWith(task -> {
            DocumentSnapshot snap = task.getResult();
            SyncState state = new SyncState();
            if (snap != null && snap.exists()) {
                Long auto = snap.getLong("lastAutoCheck");
                Long scanned = snap.getLong("lastScannedEmailDate");
                state.lastAutoCheck = auto == null ? 0 : auto;
                state.lastScannedEmailDate = scanned == null ? 0 : scanned;
                state.needsReconnect = Boolean.TRUE.equals(snap.getBoolean("needsReconnect"));
            }
            return state;
        });
    }

    public Task<Void> updateSync(SyncState state) {
        Map<String, Object> data = new HashMap<>();
        data.put("lastAutoCheck", state.lastAutoCheck);
        data.put("lastScannedEmailDate", state.lastScannedEmailDate);
        data.put("needsReconnect", state.needsReconnect);
        return doc(SYNC).set(data);
    }

    public Task<Void> savePendingReview(ImportReview review) {
        return doc(PENDING).set(Collections.singletonMap("json", ImportReviewJson.toJson(review)));
    }

    public Task<ImportReview> getPendingReview() {
        return doc(PENDING).get().continueWith(task -> {
            String json = task.getResult().getString("json");
            if (json == null) {
                return null;
            }
            try {
                return ImportReviewJson.fromJson(json);
            } catch (RuntimeException e) {
                Log.w(TAG, "Propunere salvată coruptă", e);
                return null;
            }
        });
    }

    public LiveData<ImportReview> observePendingReview() {
        DocumentReference ref = doc(PENDING);
        return new LiveData<ImportReview>() {
            private com.google.firebase.firestore.ListenerRegistration registration;

            @Override
            protected void onActive() {
                registration = ref.addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Eroare la citirea propunerii", error);
                        setValue(null);
                        return;
                    }
                    String json = snap == null ? null : snap.getString("json");
                    ImportReview review = null;
                    if (json != null) {
                        try {
                            review = ImportReviewJson.fromJson(json);
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Propunere salvată coruptă", e);
                        }
                    }
                    setValue(review);
                });
            }

            @Override
            protected void onInactive() {
                if (registration != null) {
                    registration.remove();
                    registration = null;
                }
            }
        };
    }

    public Task<Void> clearPendingReview() {
        return doc(PENDING).delete();
    }
}
