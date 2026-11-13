package com.example.proiectcmo.repository;

import androidx.annotation.StringRes;

/** Rezultatul unei operații asincrone cu Firestore (salvare, ștergere etc.). */
public interface OperationCallback {
    void onSuccess();

    /** @param messageRes id-ul mesajului de eroare din strings.xml */
    void onError(@StringRes int messageRes);
}
