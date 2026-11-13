package com.example.proiectcmo.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

/**
 * Modelul unui abonament, salvat în Firestore la calea
 * {@code users/{uid}/subscriptions/{id}}.
 *
 * Firestore are nevoie de un constructor fără parametri și de getteri/setteri publici
 * pentru a converti automat documentele în obiecte Java.
 */
public class Subscription {

    /** Abonament plătit în fiecare lună. */
    public static final String CYCLE_MONTHLY = "MONTHLY";
    /** Abonament plătit o dată pe an. */
    public static final String CYCLE_YEARLY = "YEARLY";

    @DocumentId
    private String id;
    private String name;
    private double price;
    private String currency;
    private String billingCycle;
    /** Data următoarei plăți, în milisecunde (System.currentTimeMillis()). */
    private long nextPaymentDate;
    private String category;

    /** Constructor gol, necesar pentru Firestore. */
    public Subscription() {
    }

    public Subscription(String name, double price, String currency,
                        String billingCycle, long nextPaymentDate, String category) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.nextPaymentDate = nextPaymentDate;
        this.category = category;
    }

    /**
     * Costul raportat la o lună: abonamentele anuale sunt împărțite la 12.
     * Marcat cu @Exclude ca să nu fie salvat ca un câmp separat în Firestore.
     */
    @Exclude
    public double getMonthlyCost() {
        return CYCLE_YEARLY.equals(billingCycle) ? price / 12.0 : price;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public long getNextPaymentDate() { return nextPaymentDate; }
    public void setNextPaymentDate(long nextPaymentDate) { this.nextPaymentDate = nextPaymentDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
