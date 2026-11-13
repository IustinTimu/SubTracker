package com.example.proiectcmo.utils;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * MaterialDatePicker lucrează cu date în fusul orar UTC, iar aplicația cu ora locală.
 * Aceste funcții convertesc între cele două, ca ziua aleasă să nu „sară” cu o zi.
 */
public final class DatePickerUtils {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private DatePickerUtils() {
    }

    /** Ziua aleasă în calendar (UTC) → miezul nopții în ora locală. */
    public static long utcSelectionToLocal(long utcMillis) {
        Calendar utc = Calendar.getInstance(UTC);
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }

    /** O dată locală → valoarea pe care o așteaptă calendarul (miezul nopții UTC). */
    public static long localToUtcSelection(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(UTC);
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }
}
