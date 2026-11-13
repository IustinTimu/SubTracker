package com.example.proiectcmo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proiectcmo.R;
import com.example.proiectcmo.ui.main.MainActivity;
import com.example.proiectcmo.utils.UiUtils;
import com.example.proiectcmo.viewmodel.AuthFormState;
import com.example.proiectcmo.viewmodel.AuthUiState;
import com.example.proiectcmo.viewmodel.AuthViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Primul ecran al aplicației: autentificare cu email și parolă.
 * Dacă utilizatorul este deja logat, trece direct la ecranul principal.
 */
public class LoginActivity extends AppCompatActivity {

    private AuthViewModel viewModel;

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private Button loginButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Utilizatorul s-a logat deja la o pornire anterioară → nu mai cerem parola.
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
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        UiUtils.clearErrorOnType(emailLayout);
        UiUtils.clearErrorOnType(passwordLayout);

        loginButton.setOnClickListener(v -> viewModel.login(
                UiUtils.textOf(emailLayout), UiUtils.textOf(passwordLayout)));

        TextView forgotPassword = findViewById(R.id.forgotPasswordText);
        forgotPassword.setOnClickListener(v ->
                viewModel.resetPassword(UiUtils.textOf(emailLayout)));

        TextView goToRegister = findViewById(R.id.goToRegisterText);
        goToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
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

    /** Deschide ecranul principal și închide login-ul, ca butonul Back să nu revină aici. */
    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
