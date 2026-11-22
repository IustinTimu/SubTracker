package com.example.proiectcmo.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.proiectcmo.R;
import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.importer.AnalyzerException;
import com.example.proiectcmo.importer.ImportReviewBuilder;
import com.example.proiectcmo.model.AiFinding;
import com.example.proiectcmo.model.EmailExcerpt;
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.repository.GmailAuthException;
import com.example.proiectcmo.repository.GmailRepository;
import com.example.proiectcmo.repository.ImportStateRepository;
import com.example.proiectcmo.repository.OperationCallback;
import com.example.proiectcmo.repository.SubscriptionAnalyzer;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.example.proiectcmo.utils.Event;
import com.google.android.gms.tasks.Tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GmailImportViewModel extends AndroidViewModel {
    public enum Status { INTRO, LOADING, REVIEW, MESSAGE }

    private static final String TAG = "GmailImportViewModel";
    private static final int MAX_EMAILS = 600;

    private final GmailRepository gmailRepository = new GmailRepository();
    private final SubscriptionRepository subscriptionRepository = new SubscriptionRepository();
    private final ImportStateRepository stateRepository = new ImportStateRepository();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.INTRO);
    private final MutableLiveData<ImportReview> review = new MutableLiveData<>();
    private final MutableLiveData<String> progressText = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> finished = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> saveError = new MutableLiveData<>();

    private LiveData<ImportReview> pendingSource;
    private Observer<ImportReview> pendingObserver;

    public GmailImportViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Status> getStatus() { return status; }

    public LiveData<ImportReview> getReview() { return review; }

    public LiveData<String> getProgressText() { return progressText; }

    public LiveData<Integer> getSelectedCount() { return selectedCount; }

    public LiveData<Integer> getMessage() { return message; }

    public LiveData<Event<Integer>> getFinished() { return finished; }

    public LiveData<Event<Integer>> getSaveError() { return saveError; }

    public void startManualImport(String token) {
        status.setValue(Status.LOADING);
        progressText.setValue(getApplication().getString(R.string.import_progress_reading));
        executor.execute(() -> {
            try {
                runImport(token);
            } catch (GmailAuthException e) {
                Log.w(TAG, "Accesul la Gmail a fost refuzat", e);
                postMessage(R.string.import_error_gmail_auth);
            } catch (IOException e) {
                Log.w(TAG, "Citirea Gmail a eșuat: " + e.getClass().getSimpleName());
                postMessage(R.string.import_error_gmail);
            } catch (AnalyzerException e) {
                Log.w(TAG, "Analiza Gemini a eșuat: " + e.getKind());
                postMessage(messageFor(e.getKind()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postMessage(R.string.error_generic);
            } catch (Exception e) {
                Log.w(TAG, "Importul a eșuat", e);
                postMessage(R.string.error_generic);
            }
        });
    }

    private void runImport(String token) throws Exception {
        List<Subscription> existing = Tasks.await(subscriptionRepository.fetchAll());
        ImportStateRepository.SyncState sync = Tasks.await(stateRepository.getSync());
        GmailRepository.ExcerptBatch batch = gmailRepository.fetchExcerpts(getApplication(), token, 0, MAX_EMAILS);
        if (!batch.excerpts.isEmpty()
                && !Boolean.TRUE.equals(Tasks.await(stateRepository.tryConsumeAnalysis()))) {
            postMessage(R.string.import_limit_reached);
            return;
        }

        List<AiFinding> findings = new ArrayList<>();
        int size = SubscriptionAnalyzer.CHUNK_SIZE;
        int calls = (batch.excerpts.size() + size - 1) / size;
        SubscriptionAnalyzer analyzer = calls > 0 ? new SubscriptionAnalyzer() : null;
        for (int i = 0; i < calls; i++) {
            progressText.postValue(getApplication().getString(
                    R.string.import_progress_analyzing, i + 1, calls));
            List<EmailExcerpt> chunk = batch.excerpts.subList(i * size,
                    Math.min((i + 1) * size, batch.excerpts.size()));
            findings.addAll(analyzer.analyze(chunk, existing, batch.windowComplete));
        }

        ImportReview result = ImportReviewBuilder.build(findings, existing,
                SubscriptionCatalog.getInstance(getApplication())::findMatching,
                System.currentTimeMillis());
        if (result.isEmpty()) {
            Tasks.await(subscriptionRepository.syncLastCharges(result.lastChargeSync));
        } else {
            Tasks.await(stateRepository.savePendingReview(result));
        }

        ImportStateRepository.SyncState next = new ImportStateRepository.SyncState();
        next.lastAutoCheck = System.currentTimeMillis();
        next.lastScannedEmailDate = Math.max(sync.lastScannedEmailDate, batch.newestEmailDate);
        next.needsReconnect = false;
        Tasks.await(stateRepository.updateSync(next));

        if (result.isEmpty()) {
            postMessage(R.string.import_nothing_new);
        } else {
            showReview(result, true);
        }
    }

    public void openPendingReview() {
        status.setValue(Status.LOADING);
        progressText.setValue(getApplication().getString(R.string.import_progress_reading));
        pendingSource = stateRepository.observePendingReview();
        pendingObserver = pending -> {
            stopObservingPending();
            if (pending == null || pending.isEmpty()) {
                message.setValue(R.string.import_no_pending);
                status.setValue(Status.MESSAGE);
            } else {
                showReview(pending, false);
            }
        };
        pendingSource.observeForever(pendingObserver);
    }

    private void stopObservingPending() {
        if (pendingSource != null && pendingObserver != null) {
            pendingSource.removeObserver(pendingObserver);
        }
        pendingSource = null;
        pendingObserver = null;
    }

    public void onAuthorizationFailed() {
        message.setValue(R.string.gmail_error_auth);
        status.setValue(Status.MESSAGE);
    }

    public void refreshSelectedCount() {
        ImportReview current = review.getValue();
        selectedCount.setValue(current == null ? 0 : current.selected().size());
    }

    public void apply() {
        ImportReview current = review.getValue();
        if (current == null) {
            return;
        }
        status.setValue(Status.LOADING);
        progressText.setValue(getApplication().getString(R.string.import_progress_saving));
        subscriptionRepository.applyReview(current.selected(), current.lastChargeSync,
                new OperationCallback() {
                    @Override
                    public void onSuccess() {
                        stateRepository.clearPendingReview().addOnCompleteListener(task ->
                                finished.setValue(new Event<>(R.string.review_applied)));
                    }

                    @Override
                    public void onError(int messageRes) {
                        status.setValue(Status.REVIEW);
                        saveError.setValue(new Event<>(messageRes));
                    }
                });
    }

    public void ignore() {
        ImportReview current = review.getValue();
        if (current == null) {
            return;
        }
        status.setValue(Status.LOADING);
        progressText.setValue(getApplication().getString(R.string.import_progress_saving));
        subscriptionRepository.syncLastCharges(current.lastChargeSync)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Datele ultimelor plăți nu au fost salvate", task.getException());
                    }
                    return stateRepository.clearPendingReview();
                })
                .addOnSuccessListener(unused -> finished.setValue(new Event<>(0)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Propunerea nu a putut fi ignorată", e);
                    status.setValue(Status.REVIEW);
                    saveError.setValue(new Event<>(R.string.error_save));
                });
    }

    private void showReview(ImportReview result, boolean fromBackground) {
        if (fromBackground) {
            review.postValue(result);
            selectedCount.postValue(result.selected().size());
            status.postValue(Status.REVIEW);
        } else {
            review.setValue(result);
            selectedCount.setValue(result.selected().size());
            status.setValue(Status.REVIEW);
        }
    }

    private void postMessage(int messageRes) {
        message.postValue(messageRes);
        status.postValue(Status.MESSAGE);
    }

    private static int messageFor(AnalyzerException.Kind kind) {
        switch (kind) {
            case QUOTA:
                return R.string.import_error_quota;
            case APP_CHECK:
                return R.string.import_error_app_check;
            case NETWORK:
                return R.string.import_error_network;
            default:
                return R.string.import_error_invalid;
        }
    }

    @Override
    protected void onCleared() {
        stopObservingPending();
        executor.shutdownNow();
    }
}
