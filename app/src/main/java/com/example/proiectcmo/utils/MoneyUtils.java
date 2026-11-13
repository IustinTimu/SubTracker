package com.example.proiectcmo.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formatarea sumelor și conversia aproximativă în lei.
 *
 * Totalurile de pe ecranul principal și din statistici sunt afișate în lei, deci abonamentele
 * în EUR/USD sunt convertite cu un curs fix, aproximativ (nu se descarcă un curs live).
 */
public final class MoneyUtils {

    public static final String RON = "RON";
    public static final String EUR = "EUR";
    public static final String USD = "USD";
    /** Monedele disponibile în formular. */
    public static final String[] CURRENCIES = {RON, EUR, USD};

    /** Curs aproximativ: 1 EUR ≈ 5,0 lei, 1 USD ≈ 4,6 lei. */
    private static final double EUR_TO_RON = 5.0;
    private static final double USD_TO_RON = 4.6;

    private static final Locale ROMANIAN = new Locale("ro", "RO");

    private MoneyUtils() {
    }

    /** Convertește o sumă în lei. */
    public static double toRon(double amount, String currency) {
        if (EUR.equals(currency)) {
            return amount * EUR_TO_RON;
        } else if (USD.equals(currency)) {
            return amount * USD_TO_RON;
        }
        return amount;
    }

    /** Ex.: 1234.5 → „1.234,50” (format românesc, cu 2 zecimale). */
    public static String formatNumber(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(ROMANIAN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    /** Ex.: 1234.5 → „1.235” (fără zecimale, pentru totalul anual). */
    public static String formatRounded(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(ROMANIAN);
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    /** Ex.: (49.99, "RON") → „49,99 lei”, (20, "USD") → „$20,00”, (10, "EUR") → „10,00 €”. */
    public static String format(double amount, String currency) {
        if (USD.equals(currency)) {
            return "$" + formatNumber(amount);
        } else if (EUR.equals(currency)) {
            return formatNumber(amount) + " €";
        }
        return formatNumber(amount) + " lei";
    }

    /**
     * Transformă textul introdus de utilizator în număr. Acceptă atât „49,99” cât și „49.99”.
     * @return valoarea sau -1 dacă textul nu e un număr valid
     */
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
