package com.example.proiectcmo.viewmodel;

import com.example.proiectcmo.model.Subscription;

public enum BillingFilter {
    ALL, MONTHLY, YEARLY, INACTIVE;

    public boolean accepts(Subscription subscription) {
        if (subscription.isCancelled() || this == INACTIVE) {
            return subscription.isCancelled() && this == INACTIVE;
        }
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
