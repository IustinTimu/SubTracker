package com.example.proiectcmo.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.example.proiectcmo.R;
import com.example.proiectcmo.ui.edit.AddEditSubscriptionActivity;
import com.example.proiectcmo.ui.main.MainActivity;
import com.example.proiectcmo.utils.CancelLinks;

public final class NotificationHelper {
    public static final String CHANNEL_PAYMENT_ALERTS = "payment_alerts";

    private static final String OLD_CHANNEL = "payments";

    private NotificationHelper() {
    }

    public static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel(OLD_CHANNEL);
        NotificationChannel channel = new NotificationChannel(CHANNEL_PAYMENT_ALERTS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static int notificationId(@Nullable String subscriptionId) {
        return subscriptionId == null ? 0 : subscriptionId.hashCode();
    }

    public static void showPaymentAlert(Context context, String subscriptionId, String name,
                                        String amount, @Nullable String cancelUrl) {
        if (!canNotify(context)) {
            return;
        }
        createChannel(context);
        int id = notificationId(subscriptionId);
        String text = context.getString(R.string.notification_payment_text, amount, name);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_PAYMENT_ALERTS)
                        .setSmallIcon(R.drawable.ic_bell)
                        .setColor(ContextCompat.getColor(context, R.color.st_primary))
                        .setContentTitle(context.getString(R.string.notification_payment_title, name))
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(openSubscription(context, subscriptionId, id))
                        .setAutoCancel(true)
                        .addAction(0, context.getString(R.string.notification_action_cancel),
                                cancelNow(context, subscriptionId, cancelUrl, id))
                        .addAction(0, context.getString(R.string.notification_action_keep),
                                NotificationActionReceiver.keepIntent(context, id));
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException e) {
        }
    }

    private static PendingIntent openSubscription(Context context, String subscriptionId, int id) {
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(AddEditSubscriptionActivity.editIntent(context, subscriptionId))
                .getPendingIntent(id, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent cancelNow(Context context, String subscriptionId,
                                           @Nullable String cancelUrl, int id) {
        if (cancelUrl != null) {
            return NotificationActionReceiver.cancelIntent(context, id, cancelUrl);
        }
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(AddEditSubscriptionActivity.editIntent(context, subscriptionId)
                        .putExtra(AddEditSubscriptionActivity.EXTRA_SHOW_CANCEL, true))
                .getPendingIntent(id + 1,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void dismiss(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }

    static Intent browserIntent(String url) {
        return CancelLinks.browserIntent(url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
