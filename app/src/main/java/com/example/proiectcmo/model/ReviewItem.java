package com.example.proiectcmo.model;

public class ReviewItem {
    public enum Kind { ADD, UPDATE, REACTIVATE, DEACTIVATE, INFO }

    public Kind kind;
    public Subscription proposed;
    public Subscription previous;
    public String reason;
    public String cancelUrl;
    public boolean selected;

    public ReviewItem() {
    }

    public ReviewItem(Kind kind, Subscription proposed, Subscription previous, String reason,
                      String cancelUrl, boolean selected) {
        this.kind = kind;
        this.proposed = proposed;
        this.previous = previous;
        this.reason = reason;
        this.cancelUrl = cancelUrl;
        this.selected = selected;
    }
}
