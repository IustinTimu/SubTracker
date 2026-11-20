package com.example.proiectcmo.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import com.example.proiectcmo.utils.DateUtils;

import java.util.Calendar;

public class Subscription {
    public static final String CYCLE_MONTHLY = "MONTHLY";

    public static final String CYCLE_YEARLY = "YEARLY";

    public static final String SOURCE_CATALOG = "CATALOG";

    public static final String SOURCE_GMAIL = "GMAIL";

    public static final String SOURCE_MANUAL = "MANUAL";

    @DocumentId
    private String id;
    private String name;
    private double price;
    private String currency;
    private String billingCycle;

    private long nextPaymentDate;

    private String category;

    private boolean notifyBeforePayment = true;

    private String cancelUrl;

    private String source;

    private boolean cancelled;

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

    @Exclude
    public double getMonthlyCost() {
        return isYearly() ? price / 12.0 : price;
    }

    @Exclude
    public boolean isYearly() {
        return CYCLE_YEARLY.equals(billingCycle);
    }

    @Exclude
    @NonNull
    public Category getCategoryEnum() {
        return Category.fromName(category);
    }

    @Exclude
    public long getUpcomingPaymentDate() {
        return paymentDateFrom(DateUtils.startOfDay(System.currentTimeMillis()));
    }

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
