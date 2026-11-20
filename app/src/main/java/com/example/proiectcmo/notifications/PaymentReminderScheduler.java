package com.example.proiectcmo.notifications;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.CancelLinks;
import com.example.proiectcmo.utils.DateUtils;
import com.example.proiectcmo.utils.MoneyUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Programează notificările „mâine se plătește X” cu WorkManager.
 *
 * Pentru fiecare abonament cu notificarea activă se programează o sarcină care rulează
 * cu o zi înainte de plată, la ora {@link #REMINDER_HOUR}. La fiecare schimbare a listei,
 * toate sarcinile vechi se anulează și se reprogramează, deci editările, ștergerile și
 * „Marchează ca plătit” sunt mereu reflectate corect.
 */
public final class PaymentReminderScheduler {

    private static final String TAG_REMINDERS = "payment_reminders";
    /** Ora la care apare notificarea (10:00). */
    private static final int REMINDER_HOUR = 10;

    private PaymentReminderScheduler() {
    }

    public static void sync(Context context, List<Subscription> subscriptions) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelAllWorkByTag(TAG_REMINDERS);

        long now = System.currentTimeMillis();
        for (Subscription subscription : subscriptions) {
            if (!subscription.isNotifyBeforePayment() || subscription.isCancelled()) {
                continue; // fără alertă pentru abonamentele anulate
            }
            Data input = new Data.Builder()
                    .putString(PaymentReminderWorker.KEY_ID, subscription.getId())
                    .putString(PaymentReminderWorker.KEY_NAME, subscription.getName())
                    .putString(PaymentReminderWorker.KEY_AMOUNT,
                            MoneyUtils.format(subscription.getPrice(), subscription.getCurrency()))
                    .putString(PaymentReminderWorker.KEY_CANCEL_URL,
                            CancelLinks.resolve(context, subscription))
                    .putString(PaymentReminderWorker.KEY_CYCLE, subscription.getBillingCycle())
                    .putLong(PaymentReminderWorker.KEY_FIRST_DATE, subscription.getNextPaymentDate())
                    .build();
            // ziua de dinainte a trecut deja → alerta pentru plata de după
            long payment = subscription.getUpcomingPaymentDate();
            if (reminderTime(payment) <= now) {
                payment = subscription.computeFollowingPaymentDate();
            }
            enqueue(workManager, input, payment, now);
        }
    }

    /**
     * Programează alerta pentru plata din {@code paymentDate}. Folosită și de
     * {@link PaymentReminderWorker}, care după fiecare alertă o programează pe următoarea,
     * ca alertele să continue chiar dacă aplicația nu este deschisă.
     */
    static void enqueue(WorkManager workManager, Data input, long paymentDate, long now) {
        long remindAt = reminderTime(paymentDate);
        if (remindAt <= now) {
            return;
        }
        Data data = new Data.Builder()
                .putAll(input)
                .putLong(PaymentReminderWorker.KEY_PAYMENT_DATE, paymentDate)
                .build();
        workManager.enqueue(new OneTimeWorkRequest.Builder(PaymentReminderWorker.class)
                .setInitialDelay(remindAt - now, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(TAG_REMINDERS)
                .build());
    }

    /** Momentul notificării: cu o zi înainte de data plății, la ora 10:00. */
    static long reminderTime(long paymentDate) {
        long dayBefore = DateUtils.startOfDay(paymentDate) - TimeUnit.DAYS.toMillis(1);
        return dayBefore + TimeUnit.HOURS.toMillis(REMINDER_HOUR);
    }

    /** Anulează toate notificările (la deconectare). */
    public static void cancelAll(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_REMINDERS);
    }
}
