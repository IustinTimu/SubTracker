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
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.GmailImportViewModel;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;

/**
 * Import din Gmail: cere acces de citire la Gmail, caută emailurile de la serviciile
 * din catalog și afișează „Abonamente găsite”, de unde utilizatorul le bifează
 * pe cele pe care vrea să le adauge.
 */
public class GmailImportActivity extends AppCompatActivity {

    private static final String TAG = "GmailImportActivity";
    /** Permisiunea cerută: doar citirea emailurilor, fără trimitere sau ștergere. */
    private static final String GMAIL_READONLY_SCOPE =
            "https://www.googleapis.com/auth/gmail.readonly";

    private GmailImportViewModel viewModel;
    private GmailFoundAdapter adapter;

    private View introPanel;
    private View loadingPanel;
    private View resultsPanel;
    private View messagePanel;
    private Button importButton;

    /** Primește răspunsul ferestrei în care utilizatorul acordă accesul la Gmail. */
    private final ActivityResultLauncher<IntentSenderRequest> consentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            return; // utilizatorul a renunțat
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
        importButton = findViewById(R.id.importButton);

        adapter = new GmailFoundAdapter(() -> viewModel.refreshSelectedCount());
        RecyclerView list = findViewById(R.id.foundList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.connectButton).setOnClickListener(v -> requestGmailAccess());
        findViewById(R.id.retryButton).setOnClickListener(v -> requestGmailAccess());
        importButton.setOnClickListener(v -> viewModel.importSelected());

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getStatus().observe(this, this::showPanel);
        viewModel.getItems().observe(this, adapter::submit);
        viewModel.getMessage().observe(this,
                text -> ((TextView) findViewById(R.id.messageText)).setText(text));
        viewModel.getSelectedCount().observe(this, count -> {
            importButton.setText(getString(R.string.gmail_import_selected, count));
            importButton.setEnabled(count > 0);
        });
        viewModel.getImported().observe(this, event -> {
            if (event.getIfNotHandled() != null) {
                Toast.makeText(this, R.string.gmail_imported, Toast.LENGTH_SHORT).show();
                finish(); // înapoi pe ecranul principal, unde apar abonamentele noi
            }
        });
        viewModel.getSaveError().observe(this, event -> {
            Integer messageRes = event.getIfNotHandled();
            if (messageRes != null) {
                Snackbar.make(findViewById(R.id.main), messageRes, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** Afișează un singur panou, în funcție de starea din ViewModel. */
    private void showPanel(GmailImportViewModel.Status status) {
        introPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.INTRO));
        loadingPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.LOADING));
        resultsPanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.RESULTS));
        messagePanel.setVisibility(visibleIf(status == GmailImportViewModel.Status.MESSAGE));
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    /**
     * Cere de la Google acces de citire la Gmail. La prima cerere, Google afișează
     * o fereastră de consimțământ; dacă accesul a fost deja acordat, primim direct token-ul.
     */
    private void requestGmailAccess() {
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(GMAIL_READONLY_SCOPE)))
                .build();

        Identity.getAuthorizationClient(this).authorize(request)
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
            viewModel.scan(token);
        }
    }
}
