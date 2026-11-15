package com.example.proiectcmo.viewmodel;

import androidx.annotation.Nullable;

import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.MoneyUtils;

import java.util.List;

/**
 * Datele afișate în cardul „Total lunar” de pe ecranul principal.
 * Se calculează din toată lista (nu doar din cea filtrată), în lei, fără abonamentele anulate.
 */
public final class HomeSummary {

    private final double monthlyTotal;
    private final int count;
    @Nullable private final Subscription nextPayment;

    private HomeSummary(double monthlyTotal, int count, @Nullable Subscription nextPayment) {
        this.monthlyTotal = monthlyTotal;
        this.count = count;
        this.nextPayment = nextPayment;
    }

    /** Calculează totalurile și găsește cea mai apropiată plată. */
    public static HomeSummary from(List<Subscription> subscriptions) {
        double monthly = 0;
        int active = 0;
        Subscription next = null;
        for (Subscription s : subscriptions) {
            if (s.isCancelled()) {
                continue; // abonamentele anulate nu mai costă nimic
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
