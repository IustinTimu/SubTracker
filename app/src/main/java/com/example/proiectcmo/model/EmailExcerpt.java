package com.example.proiectcmo.model;

public final class EmailExcerpt {
    public final String id;
    public final long date;
    public final String from;
    public final String subject;
    public final String text;

    public EmailExcerpt(String id, long date, String from, String subject, String text) {
        this.id = id;
        this.date = date;
        this.from = from;
        this.subject = subject;
        this.text = text;
    }
}
