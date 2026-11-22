package com.example.proiectcmo.repository;

import java.io.IOException;

public class GmailAuthException extends IOException {
    private final int code;

    public GmailAuthException(int code) {
        super("HTTP " + code);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
