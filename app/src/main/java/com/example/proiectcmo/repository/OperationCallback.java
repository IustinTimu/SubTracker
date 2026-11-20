package com.example.proiectcmo.repository;

import androidx.annotation.StringRes;

public interface OperationCallback {
    void onSuccess();

    void onError(@StringRes int messageRes);
}
