package com.example.proiectcmo.viewmodel;

import androidx.annotation.StringRes;

public final class AuthUiState {
    public enum Status {
        LOADING,

        SUCCESS,

        RESET_EMAIL_SENT,

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
