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
 * Ecranul de creare a unui cont nou (nume, email, parolă, confirmare parolă).
 */
public class RegisterActivity extends AppCompatActivity {

    private AuthViewModel viewModel;

    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmPasswordLayout;
    private Button registerButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_register);
        UiUtils.applySystemBarsPadding(findViewById(R.id.main));

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        bindViews();
        setupListeners();
        observeViewModel();
    }

    private void bindViews() {
        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        registerButton = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        UiUtils.clearErrorOnType(nameLayout);
        UiUtils.clearErrorOnType(emailLayout);
        UiUtils.clearErrorOnType(passwordLayout);
        UiUtils.clearErrorOnType(confirmPasswordLayout);

        registerButton.setOnClickListener(v -> viewModel.register(
                UiUtils.textOf(nameLayout),
                UiUtils.textOf(emailLayout),
                UiUtils.textOf(passwordLayout),
                UiUtils.textOf(confirmPasswordLayout)));

        // Utilizatorul are deja cont → revine la ecranul de login.
        TextView goToLogin = findViewById(R.id.goToLoginText);
        UiUtils.setLinkText(goToLogin, R.string.have_account, R.string.login_link);
        goToLogin.setOnClickListener(v -> finish());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void observeViewModel() {
        viewModel.getFormState().observe(this, this::showFormErrors);
        viewModel.getUiState().observe(this, this::renderState);
    }

    private void showFormErrors(AuthFormState form) {
        nameLayout.setError(errorText(form.getNameError()));
        emailLayout.setError(errorText(form.getEmailError()));
        passwordLayout.setError(errorText(form.getPasswordError()));
        confirmPasswordLayout.setError(errorText(form.getConfirmPasswordError()));
    }

    private CharSequence errorText(Integer messageRes) {
        return messageRes == null ? null : getString(messageRes);
    }

    private void renderState(AuthUiState state) {
        boolean loading = state.getStatus() == AuthUiState.Status.LOADING;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);

        if (state.getStatus() == AuthUiState.Status.SUCCESS) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else if (state.getStatus() == AuthUiState.Status.ERROR) {
            Snackbar.make(findViewById(R.id.main), state.getMessageRes(), Snackbar.LENGTH_LONG).show();
        }
    }
}
