package com.example.proiectcmo.utils;

import java.util.Calendar;
import java.util.TimeZone;

public final class DatePickerUtils {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private DatePickerUtils() {
    }

    public static long utcSelectionToLocal(long utcMillis) {
        Calendar utc = Calendar.getInstance(UTC);
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }

    public static long localToUtcSelection(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(UTC);
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }
}
