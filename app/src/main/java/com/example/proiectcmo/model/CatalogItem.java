package com.example.proiectcmo.model;

import java.util.Collections;
import java.util.List;

/**
 * Un abonament popular din catalog (Netflix, Spotify etc.), încărcat din
 * {@code assets/catalog.json}. Precompletează formularul de adăugare și ajută
 * la căutarea emailurilor serviciului în importul din Gmail.
 */
public final class CatalogItem {

    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;
    /**
     * Expeditorii facturilor: domenii de email (ex. "netflix.com") sau numele afișat
     * (ex. "anthropic", pentru chitanțele trimise printr-un procesator de plăți).
     */
    private final List<String> emailDomains;
    /**
     * Text care trebuie să apară în email, pentru serviciile care trimit de pe un domeniu
     * comun mai multor produse (ex. apple.com → „Apple Music”). Gol dacă nu e nevoie.
     */
    private final String keyword;

    public CatalogItem(String name, double price, String currency, String billingCycle,
                       Category category, List<String> emailDomains, String keyword) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.emailDomains = Collections.unmodifiableList(emailDomains);
        this.keyword = keyword;
    }

    public String getName() { return name; }

    public double getPrice() { return price; }

    public String getCurrency() { return currency; }

    public String getBillingCycle() { return billingCycle; }

    public Category getCategory() { return category; }

    public List<String> getEmailDomains() { return emailDomains; }

    public String getKeyword() { return keyword; }
}
