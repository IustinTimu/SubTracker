package com.example.proiectcmo.model;

import java.util.Collections;
import java.util.List;

/**
 * Un abonament popular din catalog (Netflix, Spotify etc.), încărcat din
 * {@code assets/catalog.json}. Precompletează formularul de adăugare și ajută
 * la recunoașterea expeditorului în importul din Gmail.
 */
public final class CatalogItem {

    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;
    /** Domeniile de email de pe care serviciul trimite facturi (ex. "netflix.com"). */
    private final List<String> emailDomains;

    public CatalogItem(String name, double price, String currency, String billingCycle,
                       Category category, List<String> emailDomains) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.emailDomains = Collections.unmodifiableList(emailDomains);
    }

    public String getName() { return name; }

    public double getPrice() { return price; }

    public String getCurrency() { return currency; }

    public String getBillingCycle() { return billingCycle; }

    public Category getCategory() { return category; }

    public List<String> getEmailDomains() { return emailDomains; }

    /** @return true dacă adresa expeditorului aparține unuia dintre domeniile serviciului. */
    public boolean matchesSender(String senderEmail) {
        if (senderEmail == null) {
            return false;
        }
        String sender = senderEmail.toLowerCase();
        for (String domain : emailDomains) {
            // potrivește atât „@netflix.com” cât și subdomenii ca „@mailer.netflix.com”
            if (sender.endsWith("@" + domain) || sender.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }
}
