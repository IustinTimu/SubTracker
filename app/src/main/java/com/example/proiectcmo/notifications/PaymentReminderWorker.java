package com.example.proiectcmo.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


/**
 * Sarcină rulată de WorkManager cu o zi înainte de plată: afișează alerta cu butoanele
 * „Anulează acum” și „Păstrez”.
 * WorkManager o execută chiar dacă aplicația e închisă sau telefonul a fost repornit.
 */
public class PaymentReminderWorker extends Worker {

    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_AMOUNT = "amount";
    static final String KEY_CANCEL_URL = "cancel_url";

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

        NotificationHelper.showPaymentAlert(context, id, name, amount,
                getInputData().getString(KEY_CANCEL_URL));
        return Result.success();
    }
}
