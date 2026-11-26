package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.ImportReview;
import com.example.proiectcmo.model.ReviewItem;

public final class AutoImportPolicy {
    public static final long DAY_MS = 86_400_000L;

    public enum Decision { SKIP_AI, RUN_AI, LIMIT_REACHED }

    private AutoImportPolicy() {
    }

    public static boolean shouldRun(long lastAutoCheck, long now) {
        return lastAutoCheck == 0 || now - lastAutoCheck >= DAY_MS;
    }

    public static boolean showReconnect(boolean needsReconnect, long lastScannedEmailDate) {
        return needsReconnect && lastScannedEmailDate > 0;
    }

    public static Decision decide(boolean hasNewEmails, boolean quotaAvailable) {
        if (!hasNewEmails) {
            return Decision.SKIP_AI;
        }
        return quotaAvailable ? Decision.RUN_AI : Decision.LIMIT_REACHED;
    }

    public static void dropAlreadyReportedLapses(ImportReview review, long lastAutoCheck) {
        if (lastAutoCheck <= 0) {
            return;
        }
        review.inactive.removeIf(item -> item.kind == ReviewItem.Kind.DEACTIVATE && !item.selected
                && item.previous != null && LapseChecker.isLapsed(item.previous, lastAutoCheck));
    }
}
