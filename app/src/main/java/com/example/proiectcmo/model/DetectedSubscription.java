package com.example.proiectcmo.model;

import java.util.Calendar;

/**
 * Un abonament propus de importul din Gmail: serviciul recunoscut după expeditor,
 * plus suma și data extrase din emailuri. Există doar în memorie, până când
 * utilizatorul îl bifează și apasă „Importă”.
 */
public class DetectedSubscription {

    private final String name;
    private final double price;
    private final String currency;
    private final String billingCycle;
    private final Category category;
    /** Data celei mai recente facturi găsite, în milisecunde. */
    private final long lastEmailDate;
    private boolean selected = true;

    public DetectedSubscription(String name, double price, String currency, String billingCycle,
                                Category category, long lastEmailDate) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.category = category;
        this.lastEmailDate = lastEmailDate;
    }

    /** Transformă propunerea într-un abonament care poate fi salvat în Firestore. */
    public Subscription toSubscription() {
        return new Subscription(name, price, currency, billingCycle,
                estimateNextPaymentDate(), category.name());
    }

    /**
     * Estimează următoarea plată: pornește de la data ultimului email și adaugă
     * câte o lună (sau un an) până ajunge în viitor.
     */
    private long estimateNextPaymentDate() {
        boolean yearly = Subscription.CYCLE_YEARLY.equals(billingCycle);
        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastEmailDate);
        while (calendar.getTimeInMillis() <= now) {
            calendar.add(yearly ? Calendar.YEAR : Calendar.MONTH, 1);
        }
        return calendar.getTimeInMillis();
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
