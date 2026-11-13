package com.example.proiectcmo.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.Calendar;

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
    /** Numele unei constante din {@link Category} (ex. "STREAMING"). */
    private String category;
    /** Dacă utilizatorul vrea notificare cu o zi înainte de plată. */
    private boolean notifyBeforePayment = true;

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
     * Costul raportat la o lună, în moneda abonamentului: cele anuale sunt împărțite la 12.
     * Marcat cu @Exclude ca să nu fie salvat ca un câmp separat în Firestore.
     */
    @Exclude
    public double getMonthlyCost() {
        return isYearly() ? price / 12.0 : price;
    }

    @Exclude
    public boolean isYearly() {
        return CYCLE_YEARLY.equals(billingCycle);
    }

    /** Categoria ca enum (câmpul din Firestore e text). */
    @Exclude
    @NonNull
    public Category getCategoryEnum() {
        return Category.fromName(category);
    }

    /**
     * Data plății de după cea curentă: +1 lună sau +1 an.
     * Folosită de „Marchează ca plătit”.
     */
    @Exclude
    public long computeFollowingPaymentDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nextPaymentDate);
        calendar.add(isYearly() ? Calendar.YEAR : Calendar.MONTH, 1);
        return calendar.getTimeInMillis();
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

    public boolean isNotifyBeforePayment() { return notifyBeforePayment; }
    public void setNotifyBeforePayment(boolean notifyBeforePayment) {
        this.notifyBeforePayment = notifyBeforePayment;
    }
}
