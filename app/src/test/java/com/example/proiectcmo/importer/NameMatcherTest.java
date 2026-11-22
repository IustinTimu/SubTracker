package com.example.proiectcmo.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NameMatcherTest {
    @Test
    public void normalizeRemovesDiacriticsAndTierWords() {
        assertEquals("icloud", NameMatcher.normalize("iCloud+"));
        assertEquals("youtube", NameMatcher.normalize("YouTube Premium"));
        assertEquals("claude", NameMatcher.normalize("Claude Pro"));
        assertEquals("televiziune", NameMatcher.normalize("Televiziune"));
    }

    @Test
    public void matchesIsContainmentAfterNormalize() {
        assertTrue(NameMatcher.matches("Apple iCloud+", "iCloud+ 50 GB"));
        assertFalse(NameMatcher.matches("Netflix", "Spotify"));
        assertFalse(NameMatcher.matches("", "Netflix"));
    }

    @Test
    public void exactNeedsEqualNormalizedNames() {
        assertTrue(NameMatcher.exact("Claude Pro", "claude"));
        assertFalse(NameMatcher.exact("Apple iCloud+", "iCloud+ 50 GB"));
    }
}
