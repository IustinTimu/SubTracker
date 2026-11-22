package com.example.proiectcmo.ui.gmail;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.repository.GmailRepository;
import com.example.proiectcmo.utils.CancelLinks;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.GmailImportViewModel;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.snackbar.Snackbar;

public class GmailImportActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_PENDING = "open_pending";

    private static final String TAG = "GmailImportActivity";

    private GmailImportViewModel viewModel;
    private ImportReviewAdapter adapter;

    private View introPanel;
    private View loadingPanel;
    private View resultsPanel;
    private View messagePanel;
    private Button applyButton;
    private View retryButton;

    private final ActivityResultLauncher<IntentSenderRequest> consentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            return;
                        }
                        try {
                            AuthorizationResult authorization = Identity.getAuthorizationClient(this)
                                    .getAuthorizationResultFromIntent(result.getData());
                            onAccessGranted(authorization);
                        } catch (ApiException e) {
                            Log.w(TAG, "Accesul la Gmail nu a fost acordat", e);
                            viewModel.onAuthorizationFailed();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_gmail_import);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));
        viewModel = new ViewModelProvider(this).get(GmailImportViewModel.class);

        ((TextView) findViewById(R.id.screenTitle)).setText(R.string.gmail_title);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        introPanel = findViewById(R.id.introPanel);
        loadingPanel = findViewById(R.id.loadingPanel);
        resultsPanel = findViewById(R.id.resultsPanel);
        messagePanel = findViewById(R.id.messagePanel);
        applyButton = findViewById(R.id.applyButton);

        adapter = new ImportReviewAdapter(new ImportReviewAdapter.Listener() {
            @Override
            public void onSelectionChanged() {
                viewModel.refreshSelectedCount();
            }

            @Override
            public void onCancelClicked(ReviewItem item) {
                CancelLinks.openOrExplain(GmailImportActivity.this,
                        item.proposed.getName(), item.cancelUrl);
            }
        });
        RecyclerView list = findViewById(R.id.reviewList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.connectButton).setOnClickListener(v -> requestGmailAccess());
        retryButton = findViewById(R.id.retryButton);
        retryButton.setOnClickListener(v -> requestGmailAccess());
        applyButton.setOnClickListener(v -> viewModel.apply());
        findViewById(R.id.ignoreButton).setOnClickListener(v -> viewModel.ignore());

        observeViewModel();
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_OPEN_PENDING, false)) {
            viewModel.openPendingReview();
        }
    }

    private void observeViewModel() {
        viewModel.getStatus().observe(this, this::showPanel);
        viewModel.getReview().observe(this, review -> adapter.submit(this, review));
        viewModel.getProgressText().observe(this,
                text -> ((TextView) findViewById(R.id.progressText)).setText(text));
        viewModel.getMessage().observe(this, messageRes -> {
            ((TextView) findViewById(R.id.messageText)).setText(messageRes);
            retryButton.setVisibility(visibleIf(!isInformational(messageRes)));
        });
        viewModel.getSelectedCount().observe(this, count -> {
            applyButton.setText(getString(R.string.review_apply, count));
            applyButton.setEnabled(count > 0);
        });
        viewModel.getFinished().observe(this, event -> {
            Integer toastRes = event.getIfNotHandled();
            if (toastRes != null) {
                if (toastRes != 0) {
                    Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        });
        viewModel.getSaveError().observe(this, event -> {
            Integer messageRes = event.getIfNotHandled();
            if (messageRes != null) {
                Snackbar.make(findViewById(R.id.main), messageRes, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showPanel(GmailImportViewModel.Status status) {
        introPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.INTRO));
        loadingPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.LOADING));
        resultsPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.REVIEW));
        messagePanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.MESSAGE));
    }

    private static boolean isInformational(int messageRes) {
        return messageRes == R.string.import_nothing_new || messageRes == R.string.import_limit_reached
                || messageRes == R.string.import_no_pending;
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    private void requestGmailAccess() {
        if (viewModel.getStatus().getValue() == GmailImportViewModel.Status.LOADING) {
            return;
        }
        Identity.getAuthorizationClient(this).authorize(GmailRepository.authorizationRequest())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution() && result.getPendingIntent() != null) {
                        consentLauncher.launch(new IntentSenderRequest.Builder(
                                result.getPendingIntent().getIntentSender()).build());
                    } else {
                        onAccessGranted(result);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Cererea de acces la Gmail a eșuat", e);
                    viewModel.onAuthorizationFailed();
                });
    }

    private void onAccessGranted(AuthorizationResult authorization) {
        String token = authorization.getAccessToken();
        if (token == null) {
            viewModel.onAuthorizationFailed();
        } else {
            viewModel.startManualImport(token);
        }
    }
}
