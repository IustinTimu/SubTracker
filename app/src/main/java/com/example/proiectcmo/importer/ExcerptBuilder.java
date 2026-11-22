package com.example.proiectcmo.importer;

import com.example.proiectcmo.model.EmailExcerpt;
import com.example.proiectcmo.utils.AmountParser;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ExcerptBuilder {
    public static final int MAX_CHARS = 1200;

    private static final Pattern DATE = Pattern.compile(
            "\\d{1,2}[./ ]\\w+[./ ]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}");

    private ExcerptBuilder() {
    }

    public static EmailExcerpt build(String id, long date, String from, String subject,
                                     String bodyText) {
        String[] lines = bodyText.split("\\r?\\n");
        StringBuilder text = new StringBuilder();
        int lastKept = -1;
        for (int i = 0; i < lines.length && text.length() < MAX_CHARS; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || !isRelevant(line)) {
                continue;
            }
            if (i > 0 && lastKept < i - 1) {
                String previous = lines[i - 1].trim();
                if (!previous.isEmpty()) {
                    text.append(previous).append('\n');
                }
            }
            text.append(line).append('\n');
            lastKept = i;
        }
        if (text.length() == 0) {
            return null;
        }
        String result = text.substring(0, Math.min(text.length(), MAX_CHARS)).trim();
        return new EmailExcerpt(id, date, from, subject, result);
    }

    private static boolean isRelevant(String line) {
        if (AmountParser.parse(line) != null || DATE.matcher(line).find()) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        for (String keyword : GmailQuery.KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
