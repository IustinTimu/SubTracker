package com.example.proiectcmo.importer;

import java.text.Normalizer;
import java.util.Locale;

public final class NameMatcher {
    private NameMatcher() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String text = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('+', ' ');
        return text.replaceAll("\\b(premium|plus|pro)\\b", " ").trim().replaceAll("\\s+", " ");
    }

    public static boolean matches(String a, String b) {
        String x = withoutVendor(normalize(a));
        String y = withoutVendor(normalize(b));
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    public static boolean exact(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private static String withoutVendor(String name) {
        return name.replaceFirst("^(apple|google|microsoft|amazon) (?=.)", "");
    }
}
