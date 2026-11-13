package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.proiectcmo.model.Subscription;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * LiveData care ascultă în timp real colecția de abonamente din Firestore.
 *
 * Listener-ul Firestore se pornește doar cât timp există un ecran activ care observă datele
 * ({@link #onActive()}) și se oprește automat când ecranul nu mai e vizibil ({@link #onInactive()}),
 * deci nu consumă date/baterie degeaba.
 */
public class SubscriptionsLiveData extends LiveData<List<Subscription>> {

    private static final String TAG = "SubscriptionsLiveData";

    private final Query query;
    private ListenerRegistration registration;

    SubscriptionsLiveData(Query query) {
        this.query = query;
    }

    @Override
    protected void onActive() {
        registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Eroare la citirea abonamentelor", error);
                return;
            }
            if (snapshot != null) {
                setValue(new ArrayList<>(snapshot.toObjects(Subscription.class)));
            }
        });
    }

    @Override
    protected void onInactive() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
