package com.example.proiectcmo.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;

import org.junit.Test;

import java.util.Calendar;

public class AutoImportPolicyTest {
    @Test public void runsWhenNeverChecked() { assertTrue(AutoImportPolicy.shouldRun(0, 1_000L)); }

    @Test public void doesNotRunTwiceInSameDay() {
        long now = 1_800_000_000_000L;
        assertFalse(AutoImportPolicy.shouldRun(now - 3_600_000L, now));
        assertTrue(AutoImportPolicy.shouldRun(now - AutoImportPolicy.DAY_MS, now));
    }

    @Test public void reconnectShowsOnlyAfterGmailWasConnected() {
        assertFalse(AutoImportPolicy.showReconnect(true, 0));
        assertFalse(AutoImportPolicy.showReconnect(false, 1_000L));
        assertTrue(AutoImportPolicy.showReconnect(true, 1_000L));
    }

    @Test public void noNewEmailsSkipsAi() {
        assertEquals(AutoImportPolicy.Decision.SKIP_AI, AutoImportPolicy.decide(false, true));
        assertEquals(AutoImportPolicy.Decision.RUN_AI, AutoImportPolicy.decide(true, true));
        assertEquals(AutoImportPolicy.Decision.LIMIT_REACHED, AutoImportPolicy.decide(true, false));
    }

    private static final long LAST_CHECK = LapseCheckerTest.date(2026, Calendar.NOVEMBER, 20);

    private static ReviewItem deactivate(long lastCharge, boolean selected) {
        Subscription s = new Subscription("Calm", 49.99, "RON", Subscription.CYCLE_MONTHLY, 0, "OTHER");
        s.setSource(Subscription.SOURCE_GMAIL);
        s.setLastChargeDate(lastCharge);
        return new ReviewItem(ReviewItem.Kind.DEACTIVATE, s, s, null, null, selected);
    }

    private static ImportReview review(ReviewItem item) {
        ImportReview review = new ImportReview();
        review.inactive.add(item);
        return review;
    }

    @Test public void lapseReportedBeforeLastCheckIsDropped() {
        ImportReview r = review(deactivate(LapseCheckerTest.date(2026, Calendar.SEPTEMBER, 1), false));
        AutoImportPolicy.dropAlreadyReportedLapses(r, LAST_CHECK);
        assertTrue(r.inactive.isEmpty());
    }

    @Test public void newlyLapsedIsKept() {
        ImportReview r = review(deactivate(LapseCheckerTest.date(2026, Calendar.OCTOBER, 14), false));
        AutoImportPolicy.dropAlreadyReportedLapses(r, LAST_CHECK);
        assertEquals(1, r.inactive.size());
    }

    @Test public void explicitCancellationIsKept() {
        ImportReview r = review(deactivate(LapseCheckerTest.date(2026, Calendar.JANUARY, 1), true));
        AutoImportPolicy.dropAlreadyReportedLapses(r, LAST_CHECK);
        assertEquals(1, r.inactive.size());
    }

    @Test public void firstRunKeepsEverything() {
        ImportReview r = review(deactivate(LapseCheckerTest.date(2026, Calendar.JANUARY, 1), false));
        AutoImportPolicy.dropAlreadyReportedLapses(r, 0);
        assertEquals(1, r.inactive.size());
    }
}
