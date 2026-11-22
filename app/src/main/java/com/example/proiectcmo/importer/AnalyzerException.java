package com.example.proiectcmo.importer;

public class AnalyzerException extends Exception {
    public enum Kind { QUOTA, APP_CHECK, NETWORK, INVALID_RESPONSE }

    private final Kind kind;

    public AnalyzerException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
