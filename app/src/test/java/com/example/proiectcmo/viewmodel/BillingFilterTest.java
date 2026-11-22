package com.example.proiectcmo.viewmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.Subscription;

import org.junit.Test;

public class BillingFilterTest {
    @Test
    public void cancelledOnlyAppearUnderInactive() {
        for (String cycle : new String[]{Subscription.CYCLE_MONTHLY, Subscription.CYCLE_YEARLY}) {
            Subscription s = new Subscription("YouTube Premium", 29.99, "RON", cycle, 0, "STREAMING");
            assertTrue(BillingFilter.ALL.accepts(s));
            assertFalse(BillingFilter.INACTIVE.accepts(s));
            s.setCancelled(true);
            assertFalse(BillingFilter.ALL.accepts(s));
            assertFalse(BillingFilter.MONTHLY.accepts(s));
            assertFalse(BillingFilter.YEARLY.accepts(s));
            assertTrue(BillingFilter.INACTIVE.accepts(s));
        }
    }
}
