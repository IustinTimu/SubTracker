package com.example.proiectcmo.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmailQueryTest {
    @Test public void fullQueryUses400Days() {
        assertTrue(GmailQuery.build(0).startsWith("newer_than:400d ("));
    }

    @Test public void incrementalQueryUsesEpochSeconds() {
        assertTrue(GmailQuery.build(1_700_000_000_000L).startsWith("after:1700000000 ("));
    }

    @Test public void incrementalDropsAlreadyScannedEmails() {
        assertFalse(GmailQuery.isNewer(1_700_000_000_000L, 1_700_000_000_000L));
        assertFalse(GmailQuery.isNewer(1_699_999_999_000L, 1_700_000_000_000L));
        assertTrue(GmailQuery.isNewer(1_700_000_000_001L, 1_700_000_000_000L));
        assertTrue(GmailQuery.isNewer(5L, 0));
    }

    @Test public void rateLimitIsRecognisedFromErrorBody() {
        assertTrue(GmailQuery.isRateLimit("{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}"));
        assertTrue(GmailQuery.isRateLimit("{\"error\":{\"errors\":[{\"reason\":\"userRateLimitExceeded\"}]}}"));
        assertFalse(GmailQuery.isRateLimit("{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}"));
        assertFalse(GmailQuery.isRateLimit(null));
    }
}
