package com.example.proiectcmo.utils;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtils {
    public static final String RON = "RON";
    public static final String EUR = "EUR";
    public static final String USD = "USD";

    public static final String[] CURRENCIES = {RON, EUR, USD};

    private static final double EUR_TO_RON = 5.0;
    private static final double USD_TO_RON = 4.6;

    private static final Locale ROMANIAN = new Locale("ro", "RO");

    private MoneyUtils() {
    }

    public static double toRon(double amount, String currency) {
        if (EUR.equals(currency)) {
            return amount * EUR_TO_RON;
        } else if (USD.equals(currency)) {
            return amount * USD_TO_RON;
        }
        return amount;
    }

    public static String formatNumber(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(ROMANIAN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    public static String formatRounded(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(ROMANIAN);
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    public static String format(double amount, String currency) {
        if (USD.equals(currency)) {
            return "$" + formatNumber(amount);
        } else if (EUR.equals(currency)) {
            return formatNumber(amount) + " €";
        }
        return formatNumber(amount) + " lei";
    }

    public static double parse(String text) {
        if (text == null) {
            return -1;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
