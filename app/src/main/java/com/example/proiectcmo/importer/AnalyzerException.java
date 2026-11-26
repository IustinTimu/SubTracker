package com.example.proiectcmo.importer;

import java.util.Locale;

public class AnalyzerException extends Exception {
    public enum Kind { QUOTA, APP_CHECK, NETWORK, BUSY, INVALID_RESPONSE }

    private final Kind kind;

    public AnalyzerException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public static boolean isBusy(String serverMessage) {
        String message = serverMessage.toLowerCase(Locale.ROOT);
        return message.contains("high demand") || message.contains("overloaded")
                || message.contains("deadline expired") || message.contains("unavailable");
    }

    public Kind getKind() {
        return kind;
    }
}
