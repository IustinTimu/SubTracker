package com.example.proiectcmo.utils;

import android.content.Context;

import com.example.proiectcmo.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DateUtils {
    private static final Locale ROMANIAN = new Locale("ro", "RO");

    private DateUtils() {
    }

    public static long startOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static int daysUntil(long millis) {
        long diff = startOfDay(millis) - startOfDay(System.currentTimeMillis());

        return (int) Math.round(diff / (double) TimeUnit.DAYS.toMillis(1));
    }

    public static String relativeDays(Context context, long millis) {
        int days = daysUntil(millis);
        if (days == 0) {
            return context.getString(R.string.due_today);
        } else if (days == 1) {
            return context.getString(R.string.due_tomorrow);
        }

        return UiUtils.quantityString(context, R.plurals.due_in_days, days, days);
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("d MMM yyyy", ROMANIAN).format(new Date(millis));
    }

    public static String formatShort(long millis) {
        return new SimpleDateFormat("d MMM", ROMANIAN).format(new Date(millis));
    }
}
