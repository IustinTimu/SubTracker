package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public final class AuthFormState {
    @Nullable private final Integer nameError;
    @Nullable private final Integer emailError;
    @Nullable private final Integer passwordError;
    @Nullable private final Integer confirmPasswordError;

    public AuthFormState(@Nullable @StringRes Integer nameError,
                         @Nullable @StringRes Integer emailError,
                         @Nullable @StringRes Integer passwordError,
                         @Nullable @StringRes Integer confirmPasswordError) {
        this.nameError = nameError;
        this.emailError = emailError;
        this.passwordError = passwordError;
        this.confirmPasswordError = confirmPasswordError;
    }

    public boolean isValid() {
        return nameError == null && emailError == null
                && passwordError == null && confirmPasswordError == null;
    }

    @Nullable public Integer getNameError() { return nameError; }

    @Nullable public Integer getEmailError() { return emailError; }

    @Nullable public Integer getPasswordError() { return passwordError; }

    @Nullable public Integer getConfirmPasswordError() { return confirmPasswordError; }
}
