package com.example.proiectcmo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proiectcmo.R;
import com.example.proiectcmo.ui.main.MainActivity;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.AuthFormState;
import com.example.proiectcmo.viewmodel.AuthUiState;
import com.example.proiectcmo.viewmodel.AuthViewModel;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private AuthViewModel viewModel;

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private Button loginButton;
    private Button googleButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (viewModel.isLoggedIn()) {
            openMainScreen();
            return;
        }

        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_login);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        bindViews();
        setupListeners();
        observeViewModel();
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        loginButton = findViewById(R.id.loginButton);
        googleButton = findViewById(R.id.googleButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        UiUtils.clearErrorOnType(emailLayout);
        UiUtils.clearErrorOnType(passwordLayout);

        loginButton.setOnClickListener(v -> viewModel.login(
                UiUtils.textOf(emailLayout), UiUtils.textOf(passwordLayout)));

        googleButton.setOnClickListener(v -> startGoogleSignIn());

        TextView forgotPassword = findViewById(R.id.forgotPasswordText);
        forgotPassword.setOnClickListener(v ->
                viewModel.resetPassword(UiUtils.textOf(emailLayout)));

        TextView goToRegister = findViewById(R.id.goToRegisterText);
        UiUtils.setLinkText(goToRegister, R.string.no_account, R.string.register_link);
        goToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void startGoogleSignIn() {
        int clientIdRes = getResources().getIdentifier(
                "default_web_client_id", "string", getPackageName());
        if (clientIdRes == 0) {
            showMessage(R.string.error_google_not_configured);
            return;
        }

        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(getString(clientIdRes)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(this).getCredentialAsync(
                this, request, null, ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            return;
                        }
                        Log.w(TAG, "Autentificarea cu Google a eșuat", e);
                        viewModel.onGoogleSignInFailed(R.string.error_google_sign_in);
                    }
                });
    }

    private void handleGoogleCredential(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                .equals(credential.getType())) {
            String idToken = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
            viewModel.loginWithGoogle(idToken);
        } else {
            viewModel.onGoogleSignInFailed(R.string.error_google_sign_in);
        }
    }

    private void observeViewModel() {
        viewModel.getFormState().observe(this, this::showFormErrors);
        viewModel.getUiState().observe(this, this::renderState);
    }

    private void showFormErrors(AuthFormState form) {
        emailLayout.setError(form.getEmailError() == null ? null : getString(form.getEmailError()));
        passwordLayout.setError(form.getPasswordError() == null ? null : getString(form.getPasswordError()));
    }

    private void renderState(AuthUiState state) {
        boolean loading = state.getStatus() == AuthUiState.Status.LOADING;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        googleButton.setEnabled(!loading);

        switch (state.getStatus()) {
            case SUCCESS:
                openMainScreen();
                break;
            case RESET_EMAIL_SENT:
                showMessage(R.string.reset_email_sent);
                break;
            case ERROR:
                showMessage(state.getMessageRes());
                break;
            default:
                break;
        }
    }

    private void showMessage(int messageRes) {
        Snackbar.make(findViewById(R.id.main), messageRes, Snackbar.LENGTH_LONG).show();
    }

    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
