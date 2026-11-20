package com.example.proiectcmo.viewmodel;

import com.example.proiectcmo.model.Subscription;

public enum BillingFilter {
    ALL, MONTHLY, YEARLY;

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
