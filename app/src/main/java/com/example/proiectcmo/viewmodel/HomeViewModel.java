package com.example.proiectcmo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.notifications.PaymentReminderScheduler;
import com.example.proiectcmo.repository.OperationCallback;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.utils.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logica ecranului principal: lista de abonamente (filtrată și sortată), totalurile
 * și acțiunile din meniul contextual (marchează ca plătit, șterge, anulează ștergerea).
 *
 * Extinde AndroidViewModel pentru că are nevoie de Context ca să programeze notificările.
 */
public class HomeViewModel extends AndroidViewModel {

    private final SubscriptionRepository repository = new SubscriptionRepository();

    /** Toate abonamentele, direct din Firestore. */
    private final LiveData<List<Subscription>> all = repository.observeAll();

    private final MutableLiveData<BillingFilter> filter = new MutableLiveData<>(BillingFilter.ALL);
    private final MutableLiveData<SortOrder> sortOrder = new MutableLiveData<>(SortOrder.NEXT_PAYMENT);

    private final MediatorLiveData<List<Subscription>> visible = new MediatorLiveData<>();
    private final MediatorLiveData<HomeSummary> summary = new MediatorLiveData<>();
    private final MutableLiveData<Event<Integer>> messages = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        // Lista vizibilă se recalculează când se schimbă datele, filtrul sau sortarea.
        visible.addSource(all, list -> updateVisible());
        visible.addSource(filter, f -> updateVisible());
        visible.addSource(sortOrder, s -> updateVisible());

        summary.addSource(all, list -> {
            summary.setValue(HomeSummary.from(list));
            // Ori de câte ori se schimbă abonamentele, reprogramăm notificările de plată.
            PaymentReminderScheduler.sync(getApplication(), list);
        });
    }

    /** Abonamentele de afișat în listă (după filtru și sortare). Null până la prima încărcare. */
    public LiveData<List<Subscription>> getVisibleSubscriptions() { return visible; }

    /** Totalurile pentru cardul principal. */
    public LiveData<HomeSummary> getSummary() { return summary; }

    /** Mesaje de afișat o singură dată (Snackbar). */
    public LiveData<Event<Integer>> getMessages() { return messages; }

    public LiveData<BillingFilter> getFilter() { return filter; }

    public LiveData<SortOrder> getSortOrder() { return sortOrder; }

    public void setFilter(BillingFilter value) { filter.setValue(value); }

    public void setSortOrder(SortOrder value) { sortOrder.setValue(value); }

    public void markAsPaid(Subscription subscription) {
        repository.markAsPaid(subscription, messageCallback(R.string.marked_paid));
    }

    public void delete(Subscription subscription) {
        // Mesajul „Abonament șters” cu butonul Anulează îl afișează activitatea imediat;
        // aici raportăm doar eventualele erori.
        repository.delete(subscription, messageCallback(0));
    }

    /** Readaugă abonamentul șters (butonul „Anulează” din Snackbar), cu același id. */
    public void undoDelete(Subscription subscription) {
        repository.save(subscription, messageCallback(0));
    }

    private void updateVisible() {
        List<Subscription> source = all.getValue();
        if (source == null) {
            return; // datele nu au sosit încă din Firestore
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

    /** Callback care publică mesajul dat la succes (0 = fără mesaj) sau eroarea. */
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
