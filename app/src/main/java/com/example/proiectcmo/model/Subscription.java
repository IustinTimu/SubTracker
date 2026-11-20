package com.example.proiectcmo.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import com.example.proiectcmo.utils.DateUtils;

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

    /** Abonament ales din catalogul de servicii populare. */
    public static final String SOURCE_CATALOG = "CATALOG";
    /** Abonament găsit de importul din Gmail. */
    public static final String SOURCE_GMAIL = "GMAIL";
    /** Abonament completat de utilizator („Personalizat”). */
    public static final String SOURCE_MANUAL = "MANUAL";

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
    /** Pagina unde se anulează abonamentul (poate lipsi la cele personalizate). */
    private String cancelUrl;
    /** De unde a fost adăugat: {@link #SOURCE_CATALOG}, {@link #SOURCE_GMAIL} sau {@link #SOURCE_MANUAL}. */
    private String source;
    /**
     * true după ce utilizatorul a anulat abonamentul pe site-ul serviciului.
     * Un abonament anulat rămâne în listă, dar nu mai intră în totaluri și nu mai primește notificări.
     */
    private boolean cancelled;

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
     * Următoarea plată, de azi încolo. Abonamentele se reînnoiesc singure, deci o dată
     * salvată care a trecut avansează cu câte o lună (sau un an) până ajunge azi sau în viitor.
     */
    @Exclude
    public long getUpcomingPaymentDate() {
        return paymentDateFrom(DateUtils.startOfDay(System.currentTimeMillis()));
    }

    /**
     * Prima dată de plată (din seria lunară / anuală) care nu e înainte de {@code from}.
     * Lunile se adaugă mereu la data inițială: din 31 ianuarie rezultă 28 februarie,
     * dar apoi 31 martie, nu 28 martie.
     */
    public long paymentDateFrom(long from) {
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(nextPaymentDate);
        Calendar calendar = (Calendar) base.clone();
        for (int steps = 1; calendar.getTimeInMillis() < from; steps++) {
            calendar = (Calendar) base.clone();
            calendar.add(isYearly() ? Calendar.YEAR : Calendar.MONTH, steps);
        }
        return calendar.getTimeInMillis();
    }

    /** Plata de după cea următoare: folosită de „Marchează ca plătit”. */
    @Exclude
    public long computeFollowingPaymentDate() {
        return paymentDateFrom(getUpcomingPaymentDate() + 1);
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

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public boolean isNotifyBeforePayment() { return notifyBeforePayment; }
    public void setNotifyBeforePayment(boolean notifyBeforePayment) {
        this.notifyBeforePayment = notifyBeforePayment;
    }
}
