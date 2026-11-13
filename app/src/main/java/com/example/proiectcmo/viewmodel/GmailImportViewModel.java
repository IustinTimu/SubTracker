package com.example.proiectcmo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proiectcmo.R;
import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.DetectedSubscription;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.repository.GmailRepository;
import com.example.proiectcmo.repository.OperationCallback;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.utils.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logica ecranului „Import din Gmail”: pornește căutarea, ține lista de abonamente
 * găsite și le salvează pe cele bifate.
 */
public class GmailImportViewModel extends AndroidViewModel {

    /** Ce panou afișează ecranul. */
    public enum Status { INTRO, LOADING, RESULTS, MESSAGE }

    private final GmailRepository gmailRepository;
    private final SubscriptionRepository subscriptionRepository = new SubscriptionRepository();

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.INTRO);
    private final MutableLiveData<List<DetectedSubscription>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> selectedCount = new MutableLiveData<>(0);
    /** Textul afișat când nu s-a găsit nimic sau a apărut o eroare. */
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> imported = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> saveError = new MutableLiveData<>();

    public GmailImportViewModel(@NonNull Application application) {
        super(application);
        gmailRepository = new GmailRepository(SubscriptionCatalog.getInstance(application));
    }

    public LiveData<Status> getStatus() { return status; }

    public LiveData<List<DetectedSubscription>> getItems() { return items; }

    public LiveData<Integer> getSelectedCount() { return selectedCount; }

    public LiveData<String> getMessage() { return message; }

    public LiveData<Event<Boolean>> getImported() { return imported; }

    public LiveData<Event<Integer>> getSaveError() { return saveError; }

    /** Caută abonamente în Gmail folosind token-ul de acces primit de la Google. */
    public void scan(String accessToken) {
        status.setValue(Status.LOADING);
        gmailRepository.findSubscriptions(accessToken, new GmailRepository.Callback() {
            @Override
            public void onResult(List<DetectedSubscription> subscriptions) {
                if (subscriptions.isEmpty()) {
                    showMessage(getApplication().getString(R.string.gmail_none_found));
                    return;
                }
                items.setValue(subscriptions);
                refreshSelectedCount();
                status.setValue(Status.RESULTS);
            }

            @Override
            public void onError(String detail) {
                showMessage(getApplication().getString(R.string.gmail_error_api, detail));
            }
        });
    }

    /** Utilizatorul nu a acordat accesul sau Google a refuzat cererea. */
    public void onAuthorizationFailed() {
        showMessage(getApplication().getString(R.string.gmail_error_auth));
    }

    /** Apelat de listă după ce o bifă a fost schimbată. */
    public void refreshSelectedCount() {
        int count = 0;
        for (DetectedSubscription item : currentItems()) {
            if (item.isSelected()) {
                count++;
            }
        }
        selectedCount.setValue(count);
    }

    /** Salvează în Firestore abonamentele bifate. */
    public void importSelected() {
        List<Subscription> toSave = new ArrayList<>();
        for (DetectedSubscription item : currentItems()) {
            if (item.isSelected()) {
                toSave.add(item.toSubscription());
            }
        }
        if (toSave.isEmpty()) {
            return;
        }
        status.setValue(Status.LOADING);
        subscriptionRepository.saveAll(toSave, new OperationCallback() {
            @Override
            public void onSuccess() {
                imported.setValue(new Event<>(true));
            }

            @Override
            public void onError(int messageRes) {
                status.setValue(Status.RESULTS);
                saveError.setValue(new Event<>(messageRes));
            }
        });
    }

    private void showMessage(String text) {
        message.setValue(text);
        status.setValue(Status.MESSAGE);
    }

    private List<DetectedSubscription> currentItems() {
        List<DetectedSubscription> list = items.getValue();
        return list == null ? Collections.emptyList() : list;
    }
}
