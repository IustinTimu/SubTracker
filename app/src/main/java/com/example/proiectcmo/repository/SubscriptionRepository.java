package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.Subscription;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Singurul loc din aplicație care citește și scrie abonamentele în Cloud Firestore.
 * Datele fiecărui utilizator stau la {@code users/{uid}/subscriptions}; regulile Firestore
 * permit accesul doar proprietarului.
 */
public class SubscriptionRepository {

    private static final String TAG = "SubscriptionRepository";
    private static final String USERS = "users";
    private static final String SUBSCRIPTIONS = "subscriptions";
    private static final String FIELD_NEXT_PAYMENT = "nextPaymentDate";
    private static final String FIELD_CANCELLED = "cancelled";

    /** Rezultatul citirii listei de abonamente. */
    public interface ListCallback {
        void onLoaded(List<Subscription> subscriptions);
    }

    /** Rezultatul încărcării unui singur abonament (pentru ecranul de editare). */
    public interface LoadCallback {
        void onLoaded(@Nullable Subscription subscription);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /** Colecția utilizatorului logat. Aruncă excepție dacă nu e nimeni logat. */
    @NonNull
    private CollectionReference collection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Niciun utilizator autentificat");
        }
        return firestore.collection(USERS).document(user.getUid()).collection(SUBSCRIPTIONS);
    }

    /** Lista abonamentelor, actualizată automat la orice modificare (ordonată după data plății). */
    public SubscriptionsLiveData observeAll() {
        return new SubscriptionsLiveData(collection().orderBy(FIELD_NEXT_PAYMENT));
    }

    /** Citește un abonament după id (pentru modul „Editează”). */
    public void getById(String id, LoadCallback callback) {
        collection().document(id).get()
                .addOnSuccessListener(doc -> callback.onLoaded(doc.toObject(Subscription.class)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Nu s-a putut citi abonamentul", e);
                    callback.onLoaded(null);
                });
    }

    /** Citește o singură dată toate abonamentele (fără actualizări automate). */
    public void getAllOnce(ListCallback callback) {
        collection().get()
                .addOnSuccessListener(snapshot ->
                        callback.onLoaded(snapshot.toObjects(Subscription.class)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Nu s-au putut citi abonamentele", e);
                    callback.onLoaded(new ArrayList<>());
                });
    }

    /** Adaugă un abonament nou (fără id) sau îl actualizează pe cel existent (cu id). */
    public void save(Subscription subscription, OperationCallback callback) {
        CollectionReference col = collection();
        if (subscription.getId() == null) {
            col.add(subscription)
                    .addOnSuccessListener(ref -> callback.onSuccess())
                    .addOnFailureListener(e -> fail(e, callback));
        } else {
            col.document(subscription.getId()).set(subscription)
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> fail(e, callback));
        }
    }

    /** Șterge abonamentul. */
    public void delete(Subscription subscription, OperationCallback callback) {
        collection().document(subscription.getId()).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    /** Mută data următoarei plăți cu o lună / un an mai târziu. */
    public void markAsPaid(Subscription subscription, OperationCallback callback) {
        collection().document(subscription.getId())
                .update(FIELD_NEXT_PAYMENT, subscription.computeFollowingPaymentDate())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    /** Marchează abonamentul ca anulat (true) sau îl reactivează (false). */
    public void setCancelled(Subscription subscription, boolean cancelled, OperationCallback callback) {
        collection().document(subscription.getId())
                .update(FIELD_CANCELLED, cancelled)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    /** Salvează mai multe abonamente deodată, într-o singură operație (importul din Gmail). */
    public void saveAll(List<Subscription> subscriptions, OperationCallback callback) {
        CollectionReference col = collection();
        WriteBatch batch = firestore.batch();
        for (Subscription subscription : subscriptions) {
            batch.set(col.document(), subscription);
        }
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    private static void fail(Exception e, OperationCallback callback) {
        Log.w(TAG, "Operație Firestore eșuată", e);
        callback.onError(R.string.error_save);
    }
}
