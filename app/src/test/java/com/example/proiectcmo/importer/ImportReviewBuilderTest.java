package com.example.proiectcmo.importer;

import static com.example.proiectcmo.importer.LapseCheckerTest.date;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.AiFinding;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public class ImportReviewBuilderTest {
    private static final long TODAY = date(2026, Calendar.NOVEMBER, 21);
    private static final long NOV_1 = date(2026, Calendar.NOVEMBER, 1);
    private static final long NOV_3 = date(2026, Calendar.NOVEMBER, 3);
    private static final long DEC_3 = date(2026, Calendar.DECEMBER, 3);

    private static final CatalogItem NETFLIX = new CatalogItem("Netflix", 59.99, "RON",
            Subscription.CYCLE_MONTHLY, Category.STREAMING,
            "https://www.netflix.com/cancelplan");
    private static final Function<String, CatalogItem> CATALOG =
            name -> NameMatcher.matches(name, "Netflix") ? NETFLIX : null;

    private static AiFinding finding(String name, String vendor, AiFinding.Status status,
                                     double price, String currency, String cycle) {
        AiFinding f = new AiFinding();
        f.name = name;
        f.vendor = vendor;
        f.status = status;
        f.price = price;
        f.currency = currency;
        f.billingCycle = cycle;
        f.lastChargeDate = NOV_3;
        f.nextPaymentDate = DEC_3;
        f.reason = "motiv";
        return f;
    }

    private static AiFinding active(String name, String vendor, double price) {
        return finding(name, vendor, AiFinding.Status.ACTIVE, price, "RON", Subscription.CYCLE_MONTHLY);
    }

    private static Subscription existing(String id, String name, String source, double price) {
        Subscription s = new Subscription(name, price, "RON", Subscription.CYCLE_MONTHLY, DEC_3, "OTHER");
        s.setId(id);
        s.setSource(source);
        s.setLastChargeDate(NOV_1);
        return s;
    }

    private static ImportReview build(List<AiFinding> findings, Subscription... existing) {
        return ImportReviewBuilder.build(findings, new ArrayList<>(Arrays.asList(existing)), CATALOG, TODAY);
    }

    @Test
    public void activeAppleAppBecomesAddWithAppleCancelUrl() {
        ImportReview review = build(Collections.singletonList(active("Calm", "Apple", 49.99)));
        assertEquals(1, review.newItems.size());
        ReviewItem item = review.newItems.get(0);
        assertEquals(ReviewItem.Kind.ADD, item.kind);
        assertEquals("https://apps.apple.com/account/subscriptions", item.cancelUrl);
        assertEquals("https://apps.apple.com/account/subscriptions", item.proposed.getCancelUrl());
        assertEquals(Category.OTHER.name(), item.proposed.getCategory());
        assertEquals(Subscription.SOURCE_GMAIL, item.proposed.getSource());
        assertEquals("Apple", item.proposed.getVendor());
        assertEquals(NOV_3, item.proposed.getLastChargeDate());
        assertTrue(item.selected);
    }

    @Test
    public void emptyCatalogCancelUrlFallsBackToVendor() {
        CatalogItem canva = new CatalogItem("Canva Pro", 59.99, "RON",
                Subscription.CYCLE_MONTHLY, Category.SOFTWARE, "");
        ImportReview review = ImportReviewBuilder.build(
                Collections.singletonList(active("Canva Pro", "canva.com", 59.99)),
                new ArrayList<>(), name -> canva, TODAY);
        ReviewItem item = review.newItems.get(0);
        assertEquals("https://canva.com", item.cancelUrl);
        assertEquals("https://canva.com", item.proposed.getCancelUrl());
        assertEquals(Category.SOFTWARE.name(), item.proposed.getCategory());
    }

    @Test
    public void weeklyOrForeignCurrencyGoesToVerifyOnly() {
        ImportReview review = build(Arrays.asList(
                finding("Medium", "medium.com", AiFinding.Status.ACTIVE, 5, "RON", "WEEKLY"),
                finding("Spectator", "spectator.co.uk", AiFinding.Status.ACTIVE, 5, "GBP", Subscription.CYCLE_MONTHLY)));
        assertEquals(2, review.toVerify.size());
        assertTrue(review.newItems.isEmpty());
        for (ReviewItem item : review.toVerify) {
            assertEquals(ReviewItem.Kind.INFO, item.kind);
            assertFalse(item.selected);
        }
        assertTrue(review.selected().isEmpty());
    }

    @Test
    public void priceChangeOnGmailSubscriptionIsUpdate() {
        Subscription claude = existing("c1", "Claude Pro", Subscription.SOURCE_GMAIL, 99.99);
        AiFinding f = active("Claude Pro", "anthropic.com", 109.99);
        f.matchesExistingId = "c1";
        ImportReview review = build(Collections.singletonList(f), claude);
        ReviewItem item = review.updates.get(0);
        assertEquals(ReviewItem.Kind.UPDATE, item.kind);
        assertEquals(109.99, item.proposed.getPrice(), 0.001);
        assertEquals(99.99, item.previous.getPrice(), 0.001);
        assertEquals("c1", item.proposed.getId());
        assertTrue(item.selected);
    }

    @Test
    public void cancelledSubscriptionWithNewChargeIsReactivated() {
        Subscription netflix = existing("n1", "Netflix", Subscription.SOURCE_CATALOG, 59.99);
        netflix.setCancelled(true);
        netflix.setLastChargeDate(NOV_1);
        AiFinding f = active("Netflix", "netflix.com", 59.99);
        f.matchesExistingId = "n1";
        f.lastChargeDate = NOV_3;
        ImportReview review = build(Collections.singletonList(f), netflix);
        ReviewItem item = review.updates.get(0);
        assertEquals(ReviewItem.Kind.REACTIVATE, item.kind);
        assertFalse(item.proposed.isCancelled());
        assertTrue(item.selected);
    }

    @Test
    public void cancelledSubscriptionWithOlderChargeIsNotReactivated() {
        Subscription netflix = existing("n1", "Netflix", Subscription.SOURCE_CATALOG, 59.99);
        netflix.setCancelled(true);
        netflix.setLastChargeDate(NOV_3);
        AiFinding f = active("Netflix", "netflix.com", 59.99);
        f.matchesExistingId = "n1";
        f.lastChargeDate = NOV_1;
        ImportReview review = build(Collections.singletonList(f), netflix);
        assertTrue(review.updates.isEmpty());
        assertTrue(review.isEmpty());
    }

    @Test
    public void cancelledSubscriptionWithoutKnownChargeIsReactivatedUnchecked() {
        Subscription netflix = existing("n1", "Netflix", Subscription.SOURCE_CATALOG, 59.99);
        netflix.setCancelled(true);
        netflix.setLastChargeDate(0);
        AiFinding f = active("Netflix", "netflix.com", 59.99);
        f.matchesExistingId = "n1";
        ReviewItem item = build(Collections.singletonList(f), netflix).updates.get(0);
        assertEquals(ReviewItem.Kind.REACTIVATE, item.kind);
        assertFalse(item.selected);
    }

    @Test
    public void canceledWithoutValidEvidenceBecomesUncertain() {
        AiFinding fake = finding("YouTube Premium", "youtube.com", AiFinding.Status.CANCELED, 25.99, "RON",
                Subscription.CYCLE_MONTHLY);
        fake.evidenceIds.add("inventat");
        AiFinding none = finding("Calm", "Apple", AiFinding.Status.CANCELED, 49.99, "RON",
                Subscription.CYCLE_MONTHLY);
        AiFinding real = finding("Netflix", "netflix.com", AiFinding.Status.CANCELED, 59.99, "RON",
                Subscription.CYCLE_MONTHLY);
        real.evidenceIds.add("m2");
        AiFinding activeNoEvidence = active("Spotify", "spotify.com", 29.99);
        List<AiFinding> checked = ImportReviewBuilder.requireEvidence(
                Arrays.asList(fake, none, real, activeNoEvidence),
                new HashSet<>(Arrays.asList("m1", "m2")));
        assertEquals(AiFinding.Status.UNCERTAIN, checked.get(0).status);
        assertEquals(AiFinding.Status.UNCERTAIN, checked.get(1).status);
        assertEquals(AiFinding.Status.CANCELED, checked.get(2).status);
        assertEquals(AiFinding.Status.ACTIVE, checked.get(3).status);

        Subscription yt = existing("y1", "YouTube Premium", Subscription.SOURCE_MANUAL, 25.99);
        fake.matchesExistingId = "y1";
        assertTrue(build(checked.subList(0, 1), yt).inactive.isEmpty());
    }

    @Test
    public void explicitCancelDeactivatesEvenManual() {
        Subscription yt = existing("y1", "YouTube Premium", Subscription.SOURCE_MANUAL, 25.99);
        AiFinding f = finding("YouTube Premium", "youtube.com", AiFinding.Status.CANCELED, 25.99, "RON",
                Subscription.CYCLE_MONTHLY);
        f.matchesExistingId = "y1";
        ImportReview review = build(Collections.singletonList(f), yt);
        ReviewItem item = review.inactive.get(0);
        assertEquals(ReviewItem.Kind.DEACTIVATE, item.kind);
        assertTrue(item.proposed.isCancelled());
        assertTrue(item.selected);
    }

    @Test
    public void lapsedIgnoredForManualAndUncheckedForGmail() {
        Subscription manual = existing("m1", "Prime Video", Subscription.SOURCE_MANUAL, 20);
        AiFinding lapsedManual = finding("Prime Video", "amazon.com", AiFinding.Status.LAPSED, 20, "RON",
                Subscription.CYCLE_MONTHLY);
        lapsedManual.matchesExistingId = "m1";
        assertTrue(build(Collections.singletonList(lapsedManual), manual).inactive.isEmpty());

        Subscription gmail = existing("g1", "Prime Video", Subscription.SOURCE_GMAIL, 20);
        AiFinding lapsedGmail = finding("Prime Video", "amazon.com", AiFinding.Status.LAPSED, 20, "RON",
                Subscription.CYCLE_MONTHLY);
        lapsedGmail.matchesExistingId = "g1";
        ImportReview review = build(Collections.singletonList(lapsedGmail), gmail);
        assertEquals(1, review.inactive.size());
        assertFalse(review.inactive.get(0).selected);
    }

    @Test
    public void unknownMatchIdIsTreatedAsNew() {
        Subscription claude = existing("c1", "Claude Pro", Subscription.SOURCE_GMAIL, 99.99);
        AiFinding f = active("Calm", "Apple", 49.99);
        f.matchesExistingId = "nu-exista";
        ImportReview review = build(Collections.singletonList(f), claude);
        assertEquals(1, review.newItems.size());
        assertTrue(review.updates.isEmpty());
        assertTrue(review.inactive.isEmpty());
    }

    @Test
    public void mismatchedNameDoesNotTouchExisting() {
        Subscription netflix = existing("n1", "Netflix", Subscription.SOURCE_CATALOG, 59.99);
        AiFinding f = active("Spotify", "spotify.com", 29.99);
        f.matchesExistingId = "n1";
        ImportReview review = build(Collections.singletonList(f), netflix);
        assertEquals(1, review.newItems.size());
        assertEquals("Spotify", review.newItems.get(0).proposed.getName());
        assertTrue(review.updates.isEmpty());
        assertTrue(review.lastChargeSync.isEmpty());
    }

    @Test
    public void duplicateAcrossChunksIsMergedKeepingLatestCharge() {
        AiFinding first = active("Calm", "Apple", 49.99);
        first.lastChargeDate = NOV_1;
        AiFinding second = active("Calm", "Apple", 49.99);
        second.lastChargeDate = NOV_3;
        ImportReview review = build(Arrays.asList(first, second));
        assertEquals(1, review.newItems.size());
        assertEquals(NOV_3, review.newItems.get(0).proposed.getLastChargeDate());
    }

    @Test
    public void unchangedActiveOnlySyncsLastCharge() {
        Subscription calm = existing("k1", "Calm", Subscription.SOURCE_GMAIL, 49.99);
        AiFinding f = active("Calm", "Apple", 49.99);
        f.matchesExistingId = "k1";
        ImportReview review = build(Collections.singletonList(f), calm);
        assertTrue(review.isEmpty());
        assertEquals(Long.valueOf(NOV_3), review.lastChargeSync.get("k1"));
    }

    @Test
    public void gmailSubscriptionWithoutFindingButLapsedIsProposedUnchecked() {
        Subscription prime = existing("p1", "Prime Video", Subscription.SOURCE_GMAIL, 20);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(TODAY);
        c.add(Calendar.DAY_OF_MONTH, -50);
        prime.setLastChargeDate(c.getTimeInMillis());
        ImportReview review = build(Collections.emptyList(), prime);
        assertEquals(1, review.inactive.size());
        ReviewItem item = review.inactive.get(0);
        assertEquals(ReviewItem.Kind.DEACTIVATE, item.kind);
        assertFalse(item.selected);
        assertNull(item.reason);
        assertEquals(c.getTimeInMillis(), item.previous.getLastChargeDate());
    }

    @Test
    public void uncertainIsIgnored() {
        AiFinding f = finding("Calm", "Apple", AiFinding.Status.UNCERTAIN, 49.99, "RON", Subscription.CYCLE_MONTHLY);
        assertTrue(build(Collections.singletonList(f)).isEmpty());
    }

    @Test
    public void exactNameWithoutIdMatchesExisting() {
        Subscription calm = existing("k1", "Calm", Subscription.SOURCE_GMAIL, 49.99);
        ImportReview review = build(Collections.singletonList(active("Calm", "Apple", 49.99)), calm);
        assertTrue(review.isEmpty());
        assertEquals(Long.valueOf(NOV_3), review.lastChargeSync.get("k1"));

        AiFinding pricier = active("Calm", "Apple", 59.99);
        ImportReview updated = build(Collections.singletonList(pricier), calm);
        assertTrue(updated.newItems.isEmpty());
        assertEquals(ReviewItem.Kind.UPDATE, updated.updates.get(0).kind);
    }

    @Test
    public void canceledWithoutIdButExactNameDeactivates() {
        Subscription yt = existing("y1", "YouTube Premium", Subscription.SOURCE_MANUAL, 25.99);
        AiFinding f = finding("YouTube", "youtube.com", AiFinding.Status.CANCELED, 25.99, "RON",
                Subscription.CYCLE_MONTHLY);
        ImportReview review = build(Collections.singletonList(f), yt);
        assertEquals(ReviewItem.Kind.DEACTIVATE, review.inactive.get(0).kind);
        assertEquals("y1", review.inactive.get(0).proposed.getId());
    }

    @Test
    public void conflictingVendorOrEmptyNormalizedNameStaysNew() {
        Subscription calm = existing("k1", "Calm", Subscription.SOURCE_GMAIL, 49.99);
        calm.setVendor("Apple");
        ImportReview review = build(Collections.singletonList(active("Calm", "calm.com", 49.99)), calm);
        assertEquals(1, review.newItems.size());
        assertTrue(review.lastChargeSync.isEmpty());

        Subscription pro = existing("p1", "Pro", Subscription.SOURCE_MANUAL, 10);
        ImportReview other = build(Collections.singletonList(active("Plus", "plus.com", 10)), pro);
        assertEquals(1, other.newItems.size());
        assertTrue(other.updates.isEmpty());
    }

    @Test
    public void staleChargeIsNotProposedAsNewEvenIfModelSaysActive() {
        AiFinding f = active("Prime Video", "Amazon", 19.99);
        f.lastChargeDate = date(2026, Calendar.SEPTEMBER, 15);
        f.nextPaymentDate = date(2026, Calendar.DECEMBER, 15);
        assertTrue(build(Collections.singletonList(f)).isEmpty());
    }

    @Test
    public void staleChargeOnImportWithoutChargeDateIsProposedInactive() {
        Subscription s = existing("prime", "Prime Video", Subscription.SOURCE_GMAIL, 19.99);
        s.setLastChargeDate(0);
        AiFinding f = active("Prime Video", "Amazon", 19.99);
        f.lastChargeDate = date(2026, Calendar.SEPTEMBER, 15);
        ImportReview review = build(Collections.singletonList(f), s);
        assertEquals(1, review.inactive.size());
        assertFalse(review.inactive.get(0).selected);
        assertTrue(review.updates.isEmpty());
    }

    @Test
    public void trialWithoutChargeStaysActive() {
        AiFinding f = active("Apple Music", "Apple", 25.99);
        f.lastChargeDate = 0;
        assertEquals(1, build(Collections.singletonList(f)).newItems.size());
    }
}
