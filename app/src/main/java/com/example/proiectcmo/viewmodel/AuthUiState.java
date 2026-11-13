package com.example.proiectcmo.viewmodel;

import androidx.annotation.StringRes;

/**
 * Starea unei operații de autentificare, observată de activități prin LiveData.
 * Activitatea decide ce afișează (progress bar, mesaj, navigare) în funcție de {@link Status}.
 */
public final class AuthUiState {

    public enum Status {
        /** Cererea a fost trimisă și se așteaptă răspunsul. */
        LOADING,
        /** Login sau înregistrare reușită. */
        SUCCESS,
        /** Emailul de resetare a parolei a fost trimis. */
        RESET_EMAIL_SENT,
        /** A apărut o eroare; detaliile sunt în {@link #getMessageRes()}. */
        ERROR
    }

    private final Status status;
    @StringRes
    private final int messageRes;

    private AuthUiState(Status status, @StringRes int messageRes) {
        this.status = status;
        this.messageRes = messageRes;
    }

    public static AuthUiState loading() { return new AuthUiState(Status.LOADING, 0); }

    public static AuthUiState success() { return new AuthUiState(Status.SUCCESS, 0); }

    public static AuthUiState resetEmailSent() { return new AuthUiState(Status.RESET_EMAIL_SENT, 0); }

    public static AuthUiState error(@StringRes int messageRes) {
        return new AuthUiState(Status.ERROR, messageRes);
    }

    public Status getStatus() { return status; }

    @StringRes
    public int getMessageRes() { return messageRes; }
}
