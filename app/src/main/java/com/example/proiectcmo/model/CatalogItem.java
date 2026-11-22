package com.example.proiectcmo.model;

public final class CatalogItem {
    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;

    private final String cancelUrl;

    public CatalogItem(String name, double price, String currency, String billingCycle,
                       Category category, String cancelUrl) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.cancelUrl = cancelUrl;
    }

    public String getName() { return name; }

    public double getPrice() { return price; }

    public String getCurrency() { return currency; }

    public String getBillingCycle() { return billingCycle; }

    public Category getCategory() { return category; }

    public String getCancelUrl() { return cancelUrl; }
}
