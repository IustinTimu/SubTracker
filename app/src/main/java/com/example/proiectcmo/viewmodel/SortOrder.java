package com.example.proiectcmo.viewmodel;

import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.MoneyUtils;

import java.util.Comparator;

public enum SortOrder {
    NEXT_PAYMENT(Comparator.comparingLong(Subscription::getUpcomingPaymentDate)),

    PRICE((a, b) -> Double.compare(monthlyRon(b), monthlyRon(a))),

    NAME((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));

    private final Comparator<Subscription> comparator;

    SortOrder(Comparator<Subscription> comparator) {
        this.comparator = comparator;
    }

    public Comparator<Subscription> comparator() {
        return comparator;
    }

    private static double monthlyRon(Subscription s) {
        return MoneyUtils.toRon(s.getMonthlyCost(), s.getCurrency());
    }
}
