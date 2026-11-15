package com.example.proiectcmo.model;


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
    /** Pagina de anulare, preluată din catalog. */
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

    /** Transformă propunerea într-un abonament care poate fi salvat în Firestore. */
    public Subscription toSubscription() {
        Subscription subscription = new Subscription(name, price, currency, billingCycle,
                lastEmailDate, category.name());
        // Emailul confirmă o plată deja făcută: următoarea este strict după acum.
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
