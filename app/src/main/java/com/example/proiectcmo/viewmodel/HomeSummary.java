package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;

import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.MoneyUtils;

import java.util.List;

public final class HomeSummary {
    private final double monthlyTotal;
    private final int count;
    @Nullable private final Subscription nextPayment;

    private HomeSummary(double monthlyTotal, int count, @Nullable Subscription nextPayment) {
        this.monthlyTotal = monthlyTotal;
        this.count = count;
        this.nextPayment = nextPayment;
    }

    public static HomeSummary from(List<Subscription> subscriptions) {
        double monthly = 0;
        int active = 0;
        Subscription next = null;
        for (Subscription s : subscriptions) {
            if (s.isCancelled()) {
                continue;
            }
            active++;
            monthly += MoneyUtils.toRon(s.getMonthlyCost(), s.getCurrency());
            if (next == null || s.getUpcomingPaymentDate() < next.getUpcomingPaymentDate()) {
                next = s;
            }
        }
        return new HomeSummary(monthly, active, next);
    }

    public double getMonthlyTotal() { return monthlyTotal; }

    public double getYearlyTotal() { return monthlyTotal * 12; }

    public int getCount() { return count; }

    @Nullable
    public Subscription getNextPayment() { return nextPayment; }
}
