package com.example.proiectcmo.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proiectcmo.R;
import com.example.proiectcmo.repository.AuthRepository;
import com.example.proiectcmo.utils.ValidationUtils;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends ViewModel {
    private final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<AuthFormState> formState = new MutableLiveData<>();
    private final MutableLiveData<AuthUiState> uiState = new MutableLiveData<>();

    public LiveData<AuthFormState> getFormState() {
        return formState;
    }

    public LiveData<AuthUiState> getUiState() {
        return uiState;
    }

    public boolean isLoggedIn() {
        return repository.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void login(String email, String password) {
        email = email.trim();
        Integer emailError = validateEmail(email);
        Integer passwordError = ValidationUtils.isNotBlank(password)
                ? null : R.string.error_password_required;

        AuthFormState form = new AuthFormState(null, emailError, passwordError, null);
        formState.setValue(form);
        if (!form.isValid()) {
            return;
        }

        uiState.setValue(AuthUiState.loading());
        repository.login(email, password, callback(AuthUiState.success()));
    }

    public void loginWithGoogle(String idToken) {
        uiState.setValue(AuthUiState.loading());
        repository.loginWithGoogle(idToken, callback(AuthUiState.success()));
    }

    public void onGoogleSignInFailed(int messageRes) {
        uiState.setValue(AuthUiState.error(messageRes));
    }

    public void register(String name, String email, String password, String confirmPassword) {
        name = name.trim();
        email = email.trim();

        Integer nameError = ValidationUtils.isNotBlank(name) ? null : R.string.error_name_required;
        Integer emailError = validateEmail(email);
        Integer passwordError = validateNewPassword(password);
        Integer confirmError = password.equals(confirmPassword)
                ? null : R.string.error_passwords_mismatch;

        AuthFormState form = new AuthFormState(nameError, emailError, passwordError, confirmError);
        formState.setValue(form);
        if (!form.isValid()) {
            return;
        }

        uiState.setValue(AuthUiState.loading());
        repository.register(name, email, password, callback(AuthUiState.success()));
    }

    public void resetPassword(String email) {
        email = email.trim();
        Integer emailError = validateEmail(email);
        formState.setValue(new AuthFormState(null, emailError, null, null));
        if (emailError != null) {
            return;
        }

        uiState.setValue(AuthUiState.loading());
        repository.sendPasswordReset(email, callback(AuthUiState.resetEmailSent()));
    }

    public void logout() {
        repository.logout();
    }

    private static Integer validateEmail(String email) {
        if (!ValidationUtils.isNotBlank(email)) {
            return R.string.error_email_required;
        }
        return ValidationUtils.isValidEmail(email) ? null : R.string.error_email_invalid;
    }

    private static Integer validateNewPassword(String password) {
        if (!ValidationUtils.isNotBlank(password)) {
            return R.string.error_password_required;
        }
        return ValidationUtils.isValidPassword(password) ? null : R.string.error_password_short;
    }

    private AuthRepository.Callback callback(AuthUiState onSuccessState) {
        return new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                uiState.setValue(onSuccessState);
            }

            @Override
            public void onError(int messageRes) {
                uiState.setValue(AuthUiState.error(messageRes));
            }
        };
    }
}
