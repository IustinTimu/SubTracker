package com.example.proiectcmo.notifications;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String ACTION_KEEP = "com.example.proiectcmo.action.KEEP";
    private static final String ACTION_CANCEL = "com.example.proiectcmo.action.CANCEL";
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";
    private static final String EXTRA_URL = "url";

    static PendingIntent keepIntent(Context context, int notificationId) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_KEEP)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return PendingIntent.getBroadcast(context, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent cancelIntent(Context context, int notificationId, String url) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_URL, url);
        return PendingIntent.getBroadcast(context, notificationId + 1, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.dismiss(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        if (ACTION_CANCEL.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null) {
                try {
                    context.startActivity(NotificationHelper.browserIntent(url));
                } catch (ActivityNotFoundException e) {
                }
            }
        }
    }
}
