package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.AiFinding;
import com.example.proiectcmo.model.CatalogItem;
import com.example.proiectcmo.model.Category;
import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;
import com.example.proiectcmo.model.Subscription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class ImportReviewBuilder {
    private static final List<String> CURRENCIES = Arrays.asList("RON", "EUR", "USD");
    private static final String APPLE_URL = "https://apps.apple.com/account/subscriptions";
    private static final String GOOGLE_PLAY_URL = "https://play.google.com/store/account/subscriptions";

    private ImportReviewBuilder() {
    }

    private static final class Match {
        AiFinding finding;
        Subscription existing;

        Match(AiFinding finding, Subscription existing) {
            this.finding = finding;
            this.existing = existing;
        }
    }

    public static ImportReview build(List<AiFinding> findings, List<Subscription> existing,
                                     Function<String, CatalogItem> catalog, long today) {
        ImportReview review = new ImportReview();
        List<Match> matches = merge(findings, existing);
        Set<String> touched = new HashSet<>();
        for (Match m : matches) {
            boolean overdue = isOverdue(m.finding, today);
            if (m.existing != null) {
                touched.add(m.existing.getId());
                applyToExisting(review, m.finding, m.existing, catalog, overdue);
            } else if (!overdue) {
                addNew(review, m.finding, catalog);
            }
        }
        for (Subscription s : existing) {
            if (!touched.contains(s.getId()) && LapseChecker.isLapsed(s, today)) {
                review.inactive.add(deactivate(s, null, catalog, false));
            }
        }
        return review;
    }

    public static List<AiFinding> requireEvidence(List<AiFinding> findings, Set<String> sentIds) {
        for (AiFinding f : findings) {
            if (f != null && f.status == AiFinding.Status.CANCELED && !anyIn(f.evidenceIds, sentIds)) {
                f.status = AiFinding.Status.UNCERTAIN;
            }
        }
        return findings;
    }

    private static boolean anyIn(List<String> ids, Set<String> sentIds) {
        for (String id : ids) {
            if (sentIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static List<Match> merge(List<AiFinding> findings, List<Subscription> existing) {
        List<Match> result = new ArrayList<>();
        for (AiFinding f : findings) {
            if (f == null || f.status == AiFinding.Status.UNCERTAIN || f.name == null) {
                continue;
            }
            Subscription target = resolve(f, existing);
            Match same = null;
            for (Match m : result) {
                boolean sameId = target != null && m.existing == target;
                boolean sameName = NameMatcher.matches(m.finding.name, f.name)
                        && equalsIgnoreCase(m.finding.vendor, f.vendor);
                if (sameId || sameName) {
                    same = m;
                    break;
                }
            }
            if (same == null) {
                result.add(new Match(f, target));
            } else {
                if (f.lastChargeDate > same.finding.lastChargeDate) {
                    same.finding = f;
                }
                if (same.existing == null) {
                    same.existing = target;
                }
            }
        }
        return result;
    }

    private static Subscription resolve(AiFinding f, List<Subscription> existing) {
        if (f.matchesExistingId != null) {
            for (Subscription s : existing) {
                if (f.matchesExistingId.equals(s.getId())) {
                    boolean sameVendor = Subscription.SOURCE_GMAIL.equals(s.getSource())
                            && s.getVendor() != null && equalsIgnoreCase(s.getVendor(), f.vendor);
                    if (NameMatcher.matches(s.getName(), f.name) || sameVendor) {
                        return s;
                    }
                }
            }
        }
        if (NameMatcher.normalize(f.name).isEmpty()) {
            return null;
        }
        for (Subscription s : existing) {
            boolean vendorsAgree = s.getVendor() == null || f.vendor == null || equalsIgnoreCase(s.getVendor(), f.vendor);
            if (vendorsAgree && NameMatcher.exact(s.getName(), f.name)) {
                return s;
            }
        }
        return null;
    }

    private static void addNew(ImportReview review, AiFinding f, Function<String, CatalogItem> catalog) {
        if (f.status != AiFinding.Status.ACTIVE) {
            return;
        }
        CatalogItem item = catalog.apply(f.name);
        String url = cancelUrl(item, f.vendor);
        Subscription s = new Subscription(f.name, f.price, f.currency, f.billingCycle, f.nextPaymentDate,
                item != null ? item.getCategory().name() : Category.OTHER.name());
        s.setSource(Subscription.SOURCE_GMAIL);
        s.setVendor(f.vendor);
        s.setLastChargeDate(f.lastChargeDate);
        s.setCancelUrl(url);
        boolean storable = isSupportedCycle(f.billingCycle) && CURRENCIES.contains(f.currency)
                && f.nextPaymentDate != 0;
        if (storable) {
            review.newItems.add(new ReviewItem(ReviewItem.Kind.ADD, s, null, f.reason, url, true));
        } else {
            review.toVerify.add(new ReviewItem(ReviewItem.Kind.INFO, s, null, f.reason, url, false));
        }
    }

    private static void applyToExisting(ImportReview review, AiFinding f, Subscription s,
                                        Function<String, CatalogItem> catalog, boolean overdue) {
        boolean gmail = Subscription.SOURCE_GMAIL.equals(s.getSource());
        if (f.status == AiFinding.Status.CANCELED) {
            if (!s.isCancelled()) {
                review.inactive.add(deactivate(s, f.reason, catalog, true));
            }
            return;
        }
        if (overdue || f.status == AiFinding.Status.LAPSED) {
            if (gmail && !s.isCancelled()) {
                ReviewItem item = deactivate(s, overdue ? null : f.reason, catalog, false);
                if (f.lastChargeDate > s.getLastChargeDate()) {
                    item.proposed.setLastChargeDate(f.lastChargeDate);
                    review.lastChargeSync.put(s.getId(), f.lastChargeDate);
                }
                review.inactive.add(item);
            }
            return;
        }
        Subscription proposed = new Subscription(s);
        if (f.lastChargeDate > s.getLastChargeDate()) {
            proposed.setLastChargeDate(f.lastChargeDate);
            review.lastChargeSync.put(s.getId(), f.lastChargeDate);
        }
        if (f.nextPaymentDate > 0) {
            proposed.setNextPaymentDate(f.nextPaymentDate);
        }
        if (proposed.getVendor() == null) {
            proposed.setVendor(f.vendor);
        }
        boolean storable = isSupportedCycle(f.billingCycle) && CURRENCIES.contains(f.currency);
        boolean changed = storable && (Math.abs(f.price - s.getPrice()) > 0.01
                || !f.currency.equals(s.getCurrency()) || !f.billingCycle.equals(s.getBillingCycle()));
        boolean mayChangePrice = gmail || NameMatcher.exact(s.getName(), f.name);
        if (changed && mayChangePrice) {
            proposed.setPrice(f.price);
            proposed.setCurrency(f.currency);
            proposed.setBillingCycle(f.billingCycle);
        }
        String url = !isBlank(s.getCancelUrl()) ? s.getCancelUrl() : cancelUrl(catalog.apply(s.getName()), f.vendor);
        if (s.isCancelled()) {
            if (f.lastChargeDate > s.getLastChargeDate()) {
                proposed.setCancelled(false);
                review.updates.add(new ReviewItem(ReviewItem.Kind.REACTIVATE, proposed, s, f.reason, url,
                        s.getLastChargeDate() > 0));
            }
        } else if (changed && mayChangePrice) {
            review.updates.add(new ReviewItem(ReviewItem.Kind.UPDATE, proposed, s, f.reason, url, true));
        }
    }

    private static boolean isOverdue(AiFinding f, long today) {
        return f.status == AiFinding.Status.ACTIVE && f.lastChargeDate > 0 && isSupportedCycle(f.billingCycle)
                && LapseChecker.isPastDue(f.lastChargeDate, Subscription.CYCLE_YEARLY.equals(f.billingCycle), today);
    }

    private static ReviewItem deactivate(Subscription s, String reason, Function<String, CatalogItem> catalog,
                                         boolean selected) {
        Subscription proposed = new Subscription(s);
        proposed.setCancelled(true);
        String url = !isBlank(s.getCancelUrl()) ? s.getCancelUrl() : cancelUrl(catalog.apply(s.getName()), s.getVendor());
        return new ReviewItem(ReviewItem.Kind.DEACTIVATE, proposed, s, reason, url, selected);
    }

    static String cancelUrl(CatalogItem item, String vendor) {
        if (item != null && !isBlank(item.getCancelUrl())) {
            return item.getCancelUrl();
        }
        String v = vendor == null ? "" : vendor.trim().toLowerCase(Locale.ROOT);
        if (v.contains("apple")) {
            return APPLE_URL;
        }
        if (v.contains("google play")) {
            return GOOGLE_PLAY_URL;
        }
        return v.matches("[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}") ? "https://" + v : null;
    }

    private static boolean isSupportedCycle(String cycle) {
        return Subscription.CYCLE_MONTHLY.equals(cycle) || Subscription.CYCLE_YEARLY.equals(cycle);
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
