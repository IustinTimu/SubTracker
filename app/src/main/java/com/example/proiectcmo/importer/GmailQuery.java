package com.example.proiectcmo.importer;

public final class GmailQuery {
    public static final String[] KEYWORDS = {
            "receipt", "chitanță", "chitanta", "factură", "factura", "invoice", "subscription",
            "abonament", "renew", "renews", "reînnoire", "trial", "canceled", "cancelled",
            "anulat", "anulare", "expired", "expirat", "payment received", "plată"
    };

    private GmailQuery() {
    }

    public static String build(long newerThanMillis) {
        StringBuilder terms = new StringBuilder();
        for (String keyword : KEYWORDS) {
            if (terms.length() > 0) {
                terms.append(" OR ");
            }
            terms.append(keyword.contains(" ") ? "\"" + keyword + "\"" : keyword);
        }
        String window = newerThanMillis <= 0 ? "newer_than:400d" : "after:" + newerThanMillis / 1000;
        return window + " (" + terms + ")";
    }

    public static boolean isNewer(long internalDate, long newerThanMillis) {
        return newerThanMillis <= 0 || internalDate > newerThanMillis;
    }

    public static boolean isRateLimit(String errorBody) {
        return errorBody != null && (errorBody.contains("\"rateLimitExceeded\"")
                || errorBody.contains("\"userRateLimitExceeded\""));
    }
}
