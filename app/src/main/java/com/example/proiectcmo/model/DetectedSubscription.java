package com.example.proiectcmo.model;

public class DetectedSubscription {
    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;

    private final long lastEmailDate;

    private final String cancelUrl;
    private boolean selected = true;

    public DetectedSubscription(String name, double price, String currency, String billingCycle,
                                Category category, long lastEmailDate, String cancelUrl) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.lastEmailDate = lastEmailDate;
        this.cancelUrl = cancelUrl;
    }

    public Subscription toSubscription() {
        Subscription subscription = new Subscription(name, price, currency, billingCycle,
                lastEmailDate, category.name());

        subscription.setNextPaymentDate(subscription.paymentDateFrom(System.currentTimeMillis() + 1));
        subscription.setSource(Subscription.SOURCE_GMAIL);
        subscription.setCancelUrl(cancelUrl);
        return subscription;
    }

    public String getName() { return name; }

    public double getPrice() { return price; }

    public String getCurrency() { return currency; }

    public String getBillingCycle() { return billingCycle; }

    public Category getCategory() { return category; }

    public long getLastEmailDate() { return lastEmailDate; }

    public boolean isSelected() { return selected; }

    public void setSelected(boolean selected) { this.selected = selected; }
}
