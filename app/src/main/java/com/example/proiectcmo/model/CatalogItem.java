package com.example.proiectcmo.model;

import java.util.Collections;
import java.util.List;

public final class CatalogItem {
    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;

    private final List<String> emailDomains;

    private final String keyword;

    private final String cancelUrl;

    public CatalogItem(String name, double price, String currency, String billingCycle,
                       Category category, List<String> emailDomains, String keyword,
                       String cancelUrl) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.emailDomains = Collections.unmodifiableList(emailDomains);
        this.keyword = keyword;
        this.cancelUrl = cancelUrl;
    }

    public String getName() { return name; }

    public double getPrice() { return price; }

    public String getCurrency() { return currency; }

    public String getBillingCycle() { return billingCycle; }

    public Category getCategory() { return category; }

    public List<String> getEmailDomains() { return emailDomains; }

    public String getKeyword() { return keyword; }

    public String getCancelUrl() { return cancelUrl; }
}
