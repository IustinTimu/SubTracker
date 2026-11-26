package com.example.proiectcmo.importer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.proiectcmo.data.SubscriptionCatalog;
import com.example.proiectcmo.model.AiFinding;
import com.example.proiectcmo.model.EmailExcerpt;
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.notifications.NotificationHelper;
import com.example.proiectcmo.repository.GmailAuthException;
import com.example.proiectcmo.repository.GmailRepository;
import com.example.proiectcmo.repository.ImportStateRepository;
import com.example.proiectcmo.repository.SubscriptionAnalyzer;
import com.example.proiectcmo.repository.SubscriptionRepository;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AutoImportWorker extends Worker {
    private static final String TAG = "AutoImportWorker";
    private static final int MAX_EMAILS = 600;

    private final ImportStateRepository stateRepository = new ImportStateRepository();
    private final SubscriptionRepository subscriptionRepository = new SubscriptionRepository();

    public AutoImportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success();
        }
        ImportStateRepository.SyncState sync;
        try {
            sync = Tasks.await(stateRepository.getSync());
        } catch (ExecutionException | RuntimeException e) {
            Log.w(TAG, "Starea sincronizării nu a putut fi citită", e);
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.success();
        }
        long now = System.currentTimeMillis();
        if (!AutoImportPolicy.shouldRun(sync.lastAutoCheck, now)) {
            return Result.success();
        }
        try {
            run(sync, now);
        } catch (GmailAuthException e) {
            Log.w(TAG, "Gmail a refuzat accesul", e);
            markChecked(sync, now, e.getCode() == HttpURLConnection.HTTP_UNAUTHORIZED
                    || sync.needsReconnect);
        } catch (IOException | AnalyzerException | ExecutionException e) {
            Log.w(TAG, "Verificarea automată a eșuat: " + e.getClass().getSimpleName());
            markChecked(sync, now, sync.needsReconnect);
        } catch (RuntimeException e) {
            Log.w(TAG, "Verificarea automată s-a oprit: " + e.getClass().getSimpleName());
            markChecked(sync, now, sync.needsReconnect);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Result.success();
    }

    private void run(ImportStateRepository.SyncState sync, long now)
            throws IOException, AnalyzerException, ExecutionException, InterruptedException {
        if (Tasks.await(stateRepository.getPendingReview()) != null) {
            markChecked(sync, now, sync.needsReconnect);
            return;
        }
        AuthorizationResult authorization = Tasks.await(Identity.getAuthorizationClient(getApplicationContext())
                .authorize(GmailRepository.authorizationRequest()));
        if (authorization.hasResolution()) {
            markChecked(sync, now, true);
            return;
        }
        String token = authorization.getAccessToken();
        if (token == null) {
            markChecked(sync, now, sync.needsReconnect);
            return;
        }
        sync.needsReconnect = false;

        List<Subscription> existing = Tasks.await(subscriptionRepository.fetchAll());
        GmailRepository.ExcerptBatch batch = new GmailRepository()
                .fetchExcerpts(getApplicationContext(), token, sync.lastScannedEmailDate, MAX_EMAILS,
                        (read, total) -> { });
        boolean windowComplete = batch.windowComplete && sync.lastScannedEmailDate == 0;
        boolean hasNewEmails = !batch.excerpts.isEmpty();
        if (hasNewEmails) {
            SubscriptionAnalyzer.verifyAccess();
        }
        boolean quota = hasNewEmails
                && Boolean.TRUE.equals(Tasks.await(stateRepository.tryConsumeAnalysis()));

        List<AiFinding> findings = new ArrayList<>();
        switch (AutoImportPolicy.decide(hasNewEmails, quota)) {
            case LIMIT_REACHED:
                markChecked(sync, now, false);
                return;
            case RUN_AI:
                SubscriptionAnalyzer analyzer = new SubscriptionAnalyzer();
                int size = SubscriptionAnalyzer.CHUNK_SIZE;
                for (int from = 0; from < batch.excerpts.size(); from += size) {
                    List<EmailExcerpt> chunk = batch.excerpts.subList(from,
                            Math.min(from + size, batch.excerpts.size()));
                    findings.addAll(analyzer.analyze(chunk, existing, windowComplete));
                }
                break;
            default:
                break;
        }

        ImportReview result = ImportReviewBuilder.build(findings, existing,
                SubscriptionCatalog.getInstance(getApplicationContext())::findMatching, now);
        AutoImportPolicy.dropAlreadyReportedLapses(result, sync.lastAutoCheck);
        if (!result.isEmpty()) {
            Tasks.await(stateRepository.savePendingReview(result));
            NotificationHelper.showImportFound(getApplicationContext(),
                    result.newItems.size(), result.inactive.size());
        }
        Tasks.await(subscriptionRepository.syncLastCharges(result.lastChargeSync));

        ImportStateRepository.SyncState next = new ImportStateRepository.SyncState();
        next.lastAutoCheck = now;
        next.lastScannedEmailDate = Math.max(sync.lastScannedEmailDate, batch.newestEmailDate);
        next.needsReconnect = false;
        Tasks.await(stateRepository.updateSync(next));
    }

    private void markChecked(ImportStateRepository.SyncState sync, long now, boolean needsReconnect) {
        ImportStateRepository.SyncState next = new ImportStateRepository.SyncState();
        next.lastAutoCheck = now;
        next.lastScannedEmailDate = sync.lastScannedEmailDate;
        next.needsReconnect = needsReconnect;
        try {
            Tasks.await(stateRepository.updateSync(next));
        } catch (ExecutionException | RuntimeException e) {
            Log.w(TAG, "Starea sincronizării nu a putut fi salvată: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
