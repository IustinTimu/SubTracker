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

    /**
     * Un număr cu separator de mii („1.199,99”, „1,199.99”, „1 199,99”) sau fără („49,99”).
     * Varianta cu mii e prima, altfel din „1.199,99” s-ar potrivi doar „199,99”.
     * Numărul nu poate începe în mijlocul altuia („2026 123,45” nu devine 26.123,45).
     */
    private static final String NUMBER =
            "(?<![\\d.,])(\\d{1,3}(?:[.,\\s]\\d{3})+(?:[.,]\\d{2})?|\\d{1,5}(?:[.,]\\d{2})?)";

    /** Număr urmat de monedă: „49,99 lei”, „9.99 EUR”, „12 €”. */
    private static final Pattern AMOUNT_THEN_CURRENCY = Pattern.compile(
            NUMBER + "\\s?(lei|ron|euro|eur|€|usd|\\$)(?![a-z])",
            Pattern.CASE_INSENSITIVE);

    /** Monedă urmată de număr: „€9.99”, „RON 49.99”, „$11.99”. */
    private static final Pattern CURRENCY_THEN_AMOUNT = Pattern.compile(
            "(lei|ron|eur|€|usd|\\$)\\s?" + NUMBER,
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
            double amount = Double.parseDouble(normalize(number));
            return amount > 0 ? new Result(amount, toCurrencyCode(currencyText)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * „1.199,99” → „1199.99”. Ultimul punct sau virgulă urmat de exact două cifre
     * este separatorul de zecimale; celelalte separatoare (de mii) se elimină.
     */
    static String normalize(String number) {
        String digits = number.replaceAll("\\s", "");
        int last = digits.length() - 3;
        boolean hasDecimals = last > 0 && (digits.charAt(last) == '.' || digits.charAt(last) == ',');
        String whole = (hasDecimals ? digits.substring(0, last) : digits).replaceAll("[.,]", "");
        return hasDecimals ? whole + "." + digits.substring(last + 1) : whole;
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
