package com.example.proiectcmo.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.proiectcmo.R;
import com.example.proiectcmo.ui.auth.LoginActivity;

/**
 * Creează canalul de notificări și afișează notificările de plată.
 * Pe Android 8+ fiecare notificare trebuie să aparțină unui canal, pe care utilizatorul
 * îl poate dezactiva din setări.
 */
public final class NotificationHelper {

    public static final String CHANNEL_PAYMENTS = "payments";

    private NotificationHelper() {
    }

    /** Creează canalul „Plăți” (dacă există deja, apelul nu are efect). */
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_PAYMENTS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** @return true dacă aplicația are voie să afișeze notificări (Android 13+ cere permisiune). */
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Afișează o notificare; la apăsare se deschide aplicația.
     * @param id identificator unic, ca notificările diferitelor abonamente să nu se suprascrie
     */
    public static void show(Context context, int id, String title, String text) {
        if (!canNotify(context)) {
            return;
        }
        createChannel(context);
        Intent intent = new Intent(context, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
                .setSmallIcon(R.drawable.ic_bell)
                .setColor(ContextCompat.getColor(context, R.color.st_primary))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException e) {
            // permisiunea a fost retrasă între verificare și afișare – nu facem nimic
        }
    }
}
