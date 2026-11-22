package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.Subscription;

import java.util.Calendar;

public final class LapseChecker {
    private LapseChecker() {
    }

    public static boolean isLapsed(Subscription s, long today) {
        if (!Subscription.SOURCE_GMAIL.equals(s.getSource()) || s.isCancelled() || s.getLastChargeDate() <= 0) {
            return false;
        }
        return isPastDue(s.getLastChargeDate(), s.isYearly(), today);
    }

    static boolean isPastDue(long lastCharge, boolean yearly, long today) {
        Calendar limit = Calendar.getInstance();
        limit.setTimeInMillis(lastCharge);
        if (yearly) {
            limit.add(Calendar.YEAR, 1);
            limit.add(Calendar.DAY_OF_MONTH, 30);
        } else {
            limit.add(Calendar.MONTH, 1);
            limit.add(Calendar.DAY_OF_MONTH, 7);
        }
        return today > limit.getTimeInMillis();
    }
}
