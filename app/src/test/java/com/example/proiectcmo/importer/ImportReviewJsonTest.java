package com.example.proiectcmo.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;

import org.junit.Test;

public class ImportReviewJsonTest {

    private static Subscription sub(String id, String name, double price, String vendor) {
        Subscription s = new Subscription();
        s.setId(id);
        s.setName(name);
        s.setPrice(price);
        s.setCurrency("RON");
        s.setBillingCycle(Subscription.CYCLE_MONTHLY);
        s.setNextPaymentDate(1700000000000L);
        s.setCategory("STREAMING");
        s.setNotifyBeforePayment(false);
        s.setSource(Subscription.SOURCE_GMAIL);
        s.setVendor(vendor);
        s.setLastChargeDate(1690000000000L);
        return s;
    }

    @Test
    public void roundTripKeepsGroupsSelectionAndFields() {
        ImportReview review = new ImportReview();
        review.newItems.add(new ReviewItem(ReviewItem.Kind.ADD, sub(null, "iCloud", 9.99, "Apple"),
                null, null, "https://cancel", true));
        review.updates.add(new ReviewItem(ReviewItem.Kind.UPDATE, sub("u1", "Netflix", 109.99, "Netflix"),
                sub("u1", "Netflix", 99.99, "Netflix"), "Preț nou", null, true));
        Subscription cancelled = sub("i1", "Spotify", 20, null);
        cancelled.setCancelled(true);
        review.inactive.add(new ReviewItem(ReviewItem.Kind.DEACTIVATE, cancelled,
                sub("i1", "Spotify", 20, null), "Fără plăți", null, false));
        review.toVerify.add(new ReviewItem(ReviewItem.Kind.INFO, sub(null, "X", 5, null),
                null, "Monedă nesuportată", null, false));
        review.lastChargeSync.put("u1", 1695000000000L);

        ImportReview back = ImportReviewJson.fromJson(ImportReviewJson.toJson(review));

        assertEquals(1, back.newItems.size());
        assertEquals(ReviewItem.Kind.DEACTIVATE, back.inactive.get(0).kind);
        assertFalse(back.inactive.get(0).selected);
        assertTrue(back.inactive.get(0).proposed.isCancelled());
        assertEquals("Apple", back.newItems.get(0).proposed.getVendor());
        assertNull(back.newItems.get(0).proposed.getId());
        assertNull(back.newItems.get(0).previous);
        assertNull(back.newItems.get(0).reason);
        assertEquals("https://cancel", back.newItems.get(0).cancelUrl);
        assertFalse(back.newItems.get(0).proposed.isNotifyBeforePayment());
        assertEquals(99.99, back.updates.get(0).previous.getPrice(), 0.001);
        assertEquals(1, back.toVerify.size());
        assertEquals(review.lastChargeSync, back.lastChargeSync);
    }
}
