package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proiectcmo.R;
import com.example.proiectcmo.utils.Event;

import com.example.proiectcmo.model.Subscription;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionsLiveData extends LiveData<List<Subscription>> {
    private static final String TAG = "SubscriptionsLiveData";

    private final Query query;
    private ListenerRegistration registration;

    private final MutableLiveData<Event<Integer>> errors = new MutableLiveData<>();

    SubscriptionsLiveData(Query query) {
        this.query = query;
    }

    public LiveData<Event<Integer>> getErrors() {
        return errors;
    }

    @Override
    protected void onActive() {
        registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Eroare la citirea abonamentelor", error);
                errors.setValue(new Event<>(R.string.error_load));
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
