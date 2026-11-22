package com.example.proiectcmo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.notifications.PaymentReminderScheduler;
import com.example.proiectcmo.repository.ImportStateRepository;
import com.example.proiectcmo.repository.OperationCallback;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.repository.SubscriptionsLiveData;
import com.example.proiectcmo.utils.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {
    private final SubscriptionRepository repository = new SubscriptionRepository();

    private final SubscriptionsLiveData all = repository.observeAll();

    private final MutableLiveData<BillingFilter> filter = new MutableLiveData<>(BillingFilter.ALL);
    private final MutableLiveData<SortOrder> sortOrder = new MutableLiveData<>(SortOrder.NEXT_PAYMENT);

    private final MediatorLiveData<List<Subscription>> visible = new MediatorLiveData<>();
    private final MediatorLiveData<HomeSummary> summary = new MediatorLiveData<>();
    private final MutableLiveData<Event<Integer>> messages = new MutableLiveData<>();

    private final ImportStateRepository importState = new ImportStateRepository();
    private final LiveData<ImportReview> pendingReview = importState.observePendingReview();
    private final MutableLiveData<Boolean> needsReconnect = new MutableLiveData<>(false);

    public HomeViewModel(@NonNull Application application) {
        super(application);

        visible.addSource(all, list -> updateVisible());
        visible.addSource(filter, f -> updateVisible());
        visible.addSource(sortOrder, s -> updateVisible());

        summary.addSource(all, list -> {
            summary.setValue(HomeSummary.from(list));

            PaymentReminderScheduler.sync(getApplication(), list);
        });
    }

    public LiveData<List<Subscription>> getVisibleSubscriptions() { return visible; }

    public LiveData<HomeSummary> getSummary() { return summary; }

    public LiveData<Event<Integer>> getMessages() { return messages; }

    public LiveData<Event<Integer>> getLoadErrors() { return all.getErrors(); }

    public LiveData<BillingFilter> getFilter() { return filter; }

    public LiveData<ImportReview> getPendingReview() { return pendingReview; }

    public LiveData<Boolean> getNeedsReconnect() { return needsReconnect; }

    public void refreshSyncState() {
        importState.getSync().addOnSuccessListener(sync -> needsReconnect.setValue(sync.needsReconnect));
    }

    public LiveData<SortOrder> getSortOrder() { return sortOrder; }

    public void setFilter(BillingFilter value) { filter.setValue(value); }

    public void setSortOrder(SortOrder value) { sortOrder.setValue(value); }

    public void markAsPaid(Subscription subscription) {
        repository.markAsPaid(subscription, messageCallback(R.string.marked_paid));
    }

    public void setCancelled(Subscription subscription, boolean cancelled) {
        repository.setCancelled(subscription, cancelled, messageCallback(
                cancelled ? R.string.marked_cancelled : R.string.marked_active));
    }

    public void delete(Subscription subscription) {
        repository.delete(subscription, messageCallback(0));
    }

    public void undoDelete(Subscription subscription) {
        repository.save(subscription, messageCallback(0));
    }

    private void updateVisible() {
        List<Subscription> source = all.getValue();
        if (source == null) {
            return;
        }
        BillingFilter currentFilter = filter.getValue();
        List<Subscription> result = new ArrayList<>();
        for (Subscription s : source) {
            if (currentFilter == null || currentFilter.accepts(s)) {
                result.add(s);
            }
        }
        SortOrder order = sortOrder.getValue();
        if (order != null) {
            Collections.sort(result, order.comparator());
        }
        visible.setValue(result);
    }

    private OperationCallback messageCallback(@StringRes int successMessage) {
        return new OperationCallback() {
            @Override
            public void onSuccess() {
                if (successMessage != 0) {
                    messages.setValue(new Event<>(successMessage));
                }
            }

            @Override
            public void onError(int messageRes) {
                messages.setValue(new Event<>(messageRes));
            }
        };
    }
}
