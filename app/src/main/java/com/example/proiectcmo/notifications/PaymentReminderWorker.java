package com.example.proiectcmo.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.proiectcmo.R;

/**
 * Sarcină rulată de WorkManager cu o zi înainte de plată: afișează notificarea.
 * WorkManager o execută chiar dacă aplicația e închisă sau telefonul a fost repornit.
 */
public class PaymentReminderWorker extends Worker {

    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_AMOUNT = "amount";

    public PaymentReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String id = getInputData().getString(KEY_ID);
        String name = getInputData().getString(KEY_NAME);
        String amount = getInputData().getString(KEY_AMOUNT);

        NotificationHelper.show(context,
                id == null ? 0 : id.hashCode(),
                context.getString(R.string.notification_payment_title, name),
                context.getString(R.string.notification_payment_text, name, amount));
        return Result.success();
    }
}
