package com.example.proiectcmo.viewmodel;

import com.example.proiectcmo.model.Subscription;

/** Filtrele de pe ecranul principal (chip-urile Toate / Lunare / Anuale). */
public enum BillingFilter {
    ALL, MONTHLY, YEARLY;

    /** @return true dacă abonamentul trebuie afișat cu acest filtru. */
    public boolean accepts(Subscription subscription) {
        switch (this) {
            case MONTHLY:
                return !subscription.isYearly();
            case YEARLY:
                return subscription.isYearly();
            default:
                return true;
        }
    }
}
