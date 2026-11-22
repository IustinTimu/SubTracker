package com.example.proiectcmo.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.Subscription;

import org.junit.Test;

import java.util.Calendar;

public class LapseCheckerTest {
    static long date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day);
        return c.getTimeInMillis();
    }

    private static Subscription gmail(String cycle, long lastCharge) {
        Subscription s = new Subscription("Calm", 49.99, "RON", cycle, 0, "OTHER");
        s.setSource(Subscription.SOURCE_GMAIL);
        s.setLastChargeDate(lastCharge);
        return s;
    }

    @Test
    public void monthlyLapsesAfterOneMonthPlusSevenDays() {
        Subscription s = gmail(Subscription.CYCLE_MONTHLY, date(2026, Calendar.OCTOBER, 1));
        assertFalse(LapseChecker.isLapsed(s, date(2026, Calendar.NOVEMBER, 8)));
        assertTrue(LapseChecker.isLapsed(s, date(2026, Calendar.NOVEMBER, 9)));
    }

    @Test
    public void yearlyToleranceIsThirtyDays() {
        Subscription s = gmail(Subscription.CYCLE_YEARLY, date(2026, Calendar.JANUARY, 1));
        assertFalse(LapseChecker.isLapsed(s, date(2027, Calendar.JANUARY, 31)));
        assertTrue(LapseChecker.isLapsed(s, date(2027, Calendar.FEBRUARY, 1)));
    }

    @Test
    public void manualOrCancelledNeverLapse() {
        long today = date(2026, Calendar.NOVEMBER, 21);
        Subscription manual = gmail(Subscription.CYCLE_MONTHLY, date(2026, Calendar.JANUARY, 1));
        manual.setSource(Subscription.SOURCE_MANUAL);
        assertFalse(LapseChecker.isLapsed(manual, today));
        Subscription cancelled = gmail(Subscription.CYCLE_MONTHLY, date(2026, Calendar.JANUARY, 1));
        cancelled.setCancelled(true);
        assertFalse(LapseChecker.isLapsed(cancelled, today));
        assertFalse(LapseChecker.isLapsed(gmail(Subscription.CYCLE_MONTHLY, 0), today));
    }
}
