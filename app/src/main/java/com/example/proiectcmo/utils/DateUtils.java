package com.example.proiectcmo.utils;

import android.content.Context;

import com.example.proiectcmo.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Calcule și formatări pentru date (zile rămase până la plată, „mâine”, „12 dec.” etc.).
 */
public final class DateUtils {

    private static final Locale ROMANIAN = new Locale("ro", "RO");

    private DateUtils() {
    }

    /** @return miezul nopții (00:00) din ziua datei date. */
    public static long startOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Numărul de zile calendaristice de azi până la data dată (negativ dacă a trecut). */
    public static int daysUntil(long millis) {
        long diff = startOfDay(millis) - startOfDay(System.currentTimeMillis());
        // Math.round compensează orele în plus/minus de la trecerea la ora de vară.
        return (int) Math.round(diff / (double) TimeUnit.DAYS.toMillis(1));
    }

    /** Ex.: „azi”, „mâine”, „în 3 zile”, „acum 2 zile”. */
    public static String relativeDays(Context context, long millis) {
        int days = daysUntil(millis);
        if (days == 0) {
            return context.getString(R.string.due_today);
        } else if (days == 1) {
            return context.getString(R.string.due_tomorrow);
        } else if (days > 1) {
            return UiUtils.quantityString(context, R.plurals.due_in_days, days, days);
        }
        return UiUtils.quantityString(context, R.plurals.overdue_days, -days, -days);
    }

    /** Ex.: „12 dec. 2026”. */
    public static String formatDate(long millis) {
        return new SimpleDateFormat("d MMM yyyy", ROMANIAN).format(new Date(millis));
    }

    /** Ex.: „12 dec.” (fără an). */
    public static String formatShort(long millis) {
        return new SimpleDateFormat("d MMM", ROMANIAN).format(new Date(millis));
    }
}
