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
import com.example.proiectcmo.ui.gmail.GmailImportActivity;
import com.example.proiectcmo.ui.main.MainActivity;
import com.example.proiectcmo.utils.CancelLinks;
import com.example.proiectcmo.utils.UiUtils;

public final class NotificationHelper {
    public static final String CHANNEL_PAYMENT_ALERTS = "payment_alerts";
    public static final String CHANNEL_IMPORT = "import_found";

    private static final int IMPORT_NOTIFICATION_ID = CHANNEL_IMPORT.hashCode();

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
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_IMPORT,
                context.getString(R.string.notification_import_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
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

    public static void cancelImportFound(Context context) {
        NotificationManagerCompat.from(context).cancel(IMPORT_NOTIFICATION_ID);
    }

    public static void showImportFound(Context context, int newCount, int inactiveCount) {
        if (!canNotify(context)) {
            return;
        }
        createChannel(context);
        String text = importFoundText(context, newCount, inactiveCount);
        PendingIntent open = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(new Intent(context, GmailImportActivity.class)
                        .putExtra(GmailImportActivity.EXTRA_OPEN_PENDING, true))
                .getPendingIntent(IMPORT_NOTIFICATION_ID,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_IMPORT)
                        .setSmallIcon(R.drawable.ic_mail)
                        .setColor(ContextCompat.getColor(context, R.color.st_primary))
                        .setContentTitle(context.getString(R.string.notification_import_title))
                        .setContentText(text)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(open)
                        .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(IMPORT_NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
        }
    }

    public static String importFoundText(Context context, int newCount, int inactiveCount) {
        String added = UiUtils.quantityString(context, R.plurals.import_found_new, newCount, newCount);
        if (newCount > 0 && inactiveCount > 0) {
            return context.getString(R.string.import_found_both, added, UiUtils.quantityString(
                    context, R.plurals.import_found_inactive_short, inactiveCount, inactiveCount));
        }
        if (newCount > 0) {
            return context.getString(R.string.import_found_one, added);
        }
        if (inactiveCount > 0) {
            return context.getString(R.string.import_found_one, UiUtils.quantityString(
                    context, R.plurals.import_found_inactive, inactiveCount, inactiveCount));
        }
        return context.getString(R.string.import_found_changes);
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
