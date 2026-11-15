package com.example.proiectcmo.utils;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caută o sumă de bani în textul unui email (ex. „Total: 49,99 lei” sau „€9.99”),
 * folosind expresii regulate.
 */
public final class AmountParser {

    /** Suma și moneda găsite într-un text. */
    public static final class Result {
        public final double amount;
        public final String currency;

        Result(double amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
    }

    /** Număr urmat de monedă: „49,99 lei”, „9.99 EUR”, „12 €”. */
    private static final Pattern AMOUNT_THEN_CURRENCY = Pattern.compile(
            "(\\d{1,5}(?:[.,]\\d{2})?)\\s?(lei|ron|euro|eur|€|usd|\\$)(?![a-z])",
            Pattern.CASE_INSENSITIVE);

    /** Monedă urmată de număr: „€9.99”, „RON 49.99”, „$11.99”. */
    private static final Pattern CURRENCY_THEN_AMOUNT = Pattern.compile(
            "(lei|ron|eur|€|usd|\\$)\\s?(\\d{1,5}(?:[.,]\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private AmountParser() {
    }

    /**
     * @return prima sumă găsită sau null. Dacă textul conține cuvântul „total”,
     * căutarea începe de acolo, pentru că de obicei suma finală apare după el.
     */
    @Nullable
    public static Result parse(String text) {
        if (text == null) {
            return null;
        }
        int totalIndex = text.toLowerCase(Locale.ROOT).indexOf("total");
        if (totalIndex >= 0) {
            Result afterTotal = firstMatch(text.substring(totalIndex));
            if (afterTotal != null) {
                return afterTotal;
            }
        }
        return firstMatch(text);
    }

    /** Câte caractere după numele produsului căutăm prețul lui. */
    private static final int NEAR_WINDOW = 160;

    /**
     * Caută suma scrisă imediat după numele unui produs. O chitanță poate conține mai multe
     * produse (ex. o chitanță Apple cu Apple Music și iCloud+), iar totalul de jos le adună;
     * de aceea luăm prețul de lângă nume.
     *
     * @return suma de după prima apariție a numelui care are un preț aproape, sau null
     */
    @Nullable
    public static Result parseNear(String text, String productName) {
        if (text == null || productName == null || productName.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = productName.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(needle);
        while (index >= 0) {
            int start = index + needle.length();
            Result near = firstMatch(text.substring(start, Math.min(text.length(), start + NEAR_WINDOW)));
            if (near != null) {
                return near;
            }
            index = lower.indexOf(needle, start);
        }
        return null;
    }

    /** Aplică ambele tipare și întoarce potrivirea care apare prima în text. */
    @Nullable
    private static Result firstMatch(String text) {
        Matcher after = AMOUNT_THEN_CURRENCY.matcher(text);
        Matcher before = CURRENCY_THEN_AMOUNT.matcher(text);
        boolean hasAfter = after.find();
        boolean hasBefore = before.find();

        if (hasAfter && (!hasBefore || after.start() <= before.start())) {
            return build(after.group(1), after.group(2));
        } else if (hasBefore) {
            return build(before.group(2), before.group(1));
        }
        return null;
    }

    @Nullable
    private static Result build(String number, String currencyText) {
        try {
            double amount = Double.parseDouble(number.replace(',', '.'));
            return amount > 0 ? new Result(amount, toCurrencyCode(currencyText)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Transformă „lei”, „€”, „$” etc. în codurile folosite de aplicație. */
    private static String toCurrencyCode(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.equals("€") || value.startsWith("eur")) {
            return MoneyUtils.EUR;
        } else if (value.equals("$") || value.equals("usd")) {
            return MoneyUtils.USD;
        }
        return MoneyUtils.RON;
    }
}
