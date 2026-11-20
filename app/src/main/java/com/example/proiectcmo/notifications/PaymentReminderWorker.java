package com.example.proiectcmo.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.proiectcmo.model.Subscription;

public class PaymentReminderWorker extends Worker {
    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_AMOUNT = "amount";
    static final String KEY_CANCEL_URL = "cancel_url";

    static final String KEY_CYCLE = "cycle";
    static final String KEY_FIRST_DATE = "first_date";

    static final String KEY_PAYMENT_DATE = "payment_date";

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

        Subscription series = new Subscription(name, 0, null,
                getInputData().getString(KEY_CYCLE), getInputData().getLong(KEY_FIRST_DATE, 0), null);
        long next = series.paymentDateFrom(getInputData().getLong(KEY_PAYMENT_DATE, 0) + 1);
        PaymentReminderScheduler.enqueue(WorkManager.getInstance(context), getInputData(),
                next, System.currentTimeMillis());
        return Result.success();
    }
}
