package com.example.proiectcmo.importer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.EmailExcerpt;

import org.junit.Test;

public class ExcerptBuilderTest {
    @Test public void appleReceiptKeepsBothProductLines() {
        String body = "Chitanța ta Apple\nApple Music Individual\nLunar 25,99 lei\niCloud+ 50 GB\nLunar 14,99 lei\nTotal 40,98 lei";
        EmailExcerpt e = ExcerptBuilder.build("m1", 1L, "no_reply@email.apple.com", "Chitanța ta de la Apple", body);
        assertTrue(e.text.contains("Apple Music Individual"));
        assertTrue(e.text.contains("14,99 lei"));
    }

    @Test public void cancellationEmailIsKept() {
        EmailExcerpt e = ExcerptBuilder.build("m2", 1L, "no-reply@youtube.com", "Abonamentul tău a fost anulat", "Your YouTube Premium membership has been canceled.");
        assertNotNull(e);
    }

    @Test public void newsletterWithoutSignalsIsDropped() {
        assertNull(ExcerptBuilder.build("m3", 1L, "news@site.com", "Noutăți", "Citește cele mai noi articole de pe blog."));
    }

    @Test public void textIsCappedAt1200Chars() {
        String body = String.join("\n", java.util.Collections.nCopies(200, "Plata lunară 9,99 lei"));
        assertTrue(ExcerptBuilder.build("m4", 1L, "a@b.c", "x", body).text.length() <= 1200);
    }
}
