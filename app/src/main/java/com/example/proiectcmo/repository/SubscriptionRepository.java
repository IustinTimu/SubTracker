package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SubscriptionRepository {
    private static final String TAG = "SubscriptionRepository";
    private static final String USERS = "users";
    private static final String SUBSCRIPTIONS = "subscriptions";
    private static final String FIELD_NEXT_PAYMENT = "nextPaymentDate";
    private static final String FIELD_CANCELLED = "cancelled";
    private static final String FIELD_LAST_CHARGE = "lastChargeDate";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_CURRENCY = "currency";
    private static final String FIELD_CYCLE = "billingCycle";
    private static final String FIELD_VENDOR = "vendor";

    public interface ListCallback {
        void onLoaded(List<Subscription> subscriptions);
    }

    public interface LoadCallback {
        void onLoaded(@Nullable Subscription subscription);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    @NonNull
    private CollectionReference collection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Niciun utilizator autentificat");
        }
        return firestore.collection(USERS).document(user.getUid()).collection(SUBSCRIPTIONS);
    }

    public SubscriptionsLiveData observeAll() {
        return new SubscriptionsLiveData(collection().orderBy(FIELD_NEXT_PAYMENT));
    }

    public void getById(String id, LoadCallback callback) {
        collection().document(id).get()
                .addOnSuccessListener(doc -> callback.onLoaded(doc.toObject(Subscription.class)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Nu s-a putut citi abonamentul", e);
                    callback.onLoaded(null);
                });
    }

    public void getAllOnce(ListCallback callback) {
        collection().get()
                .addOnSuccessListener(snapshot ->
                        callback.onLoaded(snapshot.toObjects(Subscription.class)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Nu s-au putut citi abonamentele", e);
                    callback.onLoaded(new ArrayList<>());
                });
    }

    public Task<List<Subscription>> fetchAll() {
        return collection().get().continueWith(task -> task.getResult().toObjects(Subscription.class));
    }

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

    public void delete(Subscription subscription, OperationCallback callback) {
        collection().document(subscription.getId()).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    public void markAsPaid(Subscription subscription, OperationCallback callback) {
        collection().document(subscription.getId())
                .update(FIELD_NEXT_PAYMENT, subscription.computeFollowingPaymentDate())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    public void setCancelled(Subscription subscription, boolean cancelled, OperationCallback callback) {
        collection().document(subscription.getId())
                .update(FIELD_CANCELLED, cancelled)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

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

    public void applyReview(List<ReviewItem> selected, Map<String, Long> lastChargeSync,
                            OperationCallback callback) {
        CollectionReference col = collection();
        existingIds(col).continueWithTask(task -> {
            Set<String> ids = task.getResult();
            WriteBatch batch = firestore.batch();
            for (ReviewItem item : selected) {
                if (item.kind == ReviewItem.Kind.ADD) {
                    batch.set(col.document(), item.proposed);
                } else if (item.kind != ReviewItem.Kind.INFO && ids.contains(item.proposed.getId())) {
                    Map<String, Object> fields = changedFields(item);
                    if (!fields.isEmpty()) {
                        batch.update(col.document(item.proposed.getId()), fields);
                    }
                }
            }
            addLastCharges(batch, col, ids, lastChargeSync);
            return batch.commit();
        })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> fail(e, callback));
    }

    public Task<Void> syncLastCharges(Map<String, Long> lastChargeSync) {
        CollectionReference col = collection();
        return existingIds(col).continueWithTask(task -> {
            WriteBatch batch = firestore.batch();
            addLastCharges(batch, col, task.getResult(), lastChargeSync);
            return batch.commit();
        });
    }

    private static Task<Set<String>> existingIds(CollectionReference col) {
        return col.get().continueWith(task -> {
            Set<String> ids = new HashSet<>();
            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                ids.add(doc.getId());
            }
            return ids;
        });
    }

    private static void addLastCharges(WriteBatch batch, CollectionReference col, Set<String> ids,
                                       Map<String, Long> lastChargeSync) {
        for (Map.Entry<String, Long> entry : lastChargeSync.entrySet()) {
            if (ids.contains(entry.getKey())) {
                batch.update(col.document(entry.getKey()), FIELD_LAST_CHARGE, entry.getValue());
            }
        }
    }

    private static Map<String, Object> changedFields(ReviewItem item) {
        Subscription next = item.proposed;
        Subscription prev = item.previous;
        Map<String, Object> fields = new HashMap<>();
        if (item.kind == ReviewItem.Kind.DEACTIVATE || item.kind == ReviewItem.Kind.REACTIVATE) {
            fields.put(FIELD_CANCELLED, next.isCancelled());
        }
        if (item.kind == ReviewItem.Kind.UPDATE || item.kind == ReviewItem.Kind.REACTIVATE) {
            if (prev == null || Math.abs(next.getPrice() - prev.getPrice()) > 0.001) {
                fields.put(FIELD_PRICE, next.getPrice());
            }
            if (prev == null || !Objects.equals(next.getCurrency(), prev.getCurrency())) {
                fields.put(FIELD_CURRENCY, next.getCurrency());
            }
            if (prev == null || !Objects.equals(next.getBillingCycle(), prev.getBillingCycle())) {
                fields.put(FIELD_CYCLE, next.getBillingCycle());
            }
            if (prev == null || next.getNextPaymentDate() != prev.getNextPaymentDate()) {
                fields.put(FIELD_NEXT_PAYMENT, next.getNextPaymentDate());
            }
            if (prev == null || !Objects.equals(next.getVendor(), prev.getVendor())) {
                fields.put(FIELD_VENDOR, next.getVendor());
            }
        }
        return fields;
    }

    private static void fail(Exception e, OperationCallback callback) {
        Log.w(TAG, "Operație Firestore eșuată", e);
        callback.onError(R.string.error_save);
    }
}
