package com.example.proiectcmo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proiectcmo.model.Subscription;
import com.example.proiectcmo.utils.AmountParser;

import org.junit.Test;

import java.util.Calendar;

/** Teste pentru extragerea sumelor din emailuri și pentru data următoarei plăți. */
public class AmountParserTest {

    private static void assertAmount(String text, double amount, String currency) {
        AmountParser.Result result = AmountParser.parse(text);
        assertEquals(amount, result.amount, 0.001);
        assertEquals(currency, result.currency);
    }

    @Test
    public void readsCommonFormats() {
        assertAmount("Total: 49,99 lei", 49.99, "RON");
        assertAmount("You paid €9.99 this month", 9.99, "EUR");
        assertAmount("Amount paid RON112.42", 112.42, "RON");
        assertAmount("Plan: $20.00 / month", 20.00, "USD");
    }

    @Test
    public void readsThousandsSeparators() {
        assertAmount("Abonament anual: 1.199,99 lei", 1199.99, "RON");
        assertAmount("Total RON 1,199.99", 1199.99, "RON");
        assertAmount("Total 1 199,99 lei", 1199.99, "RON");
        assertAmount("Data 1 noiembrie 2026 123,45 lei", 123.45, "RON");
    }

    @Test
    public void takesThePriceNextToTheProduct() {
        String receipt = "Apple Music Individual 25,99 lei iCloud+ 50 GB 14,99 lei Total 40,98 lei";
        assertEquals(14.99, AmountParser.parseNear(receipt, "iCloud").amount, 0.001);
        assertEquals(40.98, AmountParser.parse(receipt).amount, 0.001);
    }

    @Test
    public void returnsNullWithoutAmount() {
        assertNull(AmountParser.parse("Bun venit în noul tău abonament!"));
        assertNull(AmountParser.parseNear("Apple Music", "iCloud"));
    }

    @Test
    public void endOfMonthDatesDoNotDriftTo28() {
        Calendar jan31 = Calendar.getInstance();
        jan31.set(2026, Calendar.JANUARY, 31, 0, 0, 0);
        Subscription monthly = new Subscription("Test", 10, "RON", Subscription.CYCLE_MONTHLY,
                jan31.getTimeInMillis(), "OTHER");
        Calendar may1 = Calendar.getInstance();
        may1.set(2026, Calendar.MAY, 1, 0, 0, 0);

        Calendar result = Calendar.getInstance();
        result.setTimeInMillis(monthly.paymentDateFrom(may1.getTimeInMillis()));
        assertEquals(Calendar.MAY, result.get(Calendar.MONTH));
        assertEquals(31, result.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void upcomingPaymentRollsPastDatesForward() {
        Calendar threeMonthsAgo = Calendar.getInstance();
        threeMonthsAgo.add(Calendar.MONTH, -3);
        Subscription monthly = new Subscription("Test", 10, "RON", Subscription.CYCLE_MONTHLY,
                threeMonthsAgo.getTimeInMillis(), "OTHER");

        long upcoming = monthly.getUpcomingPaymentDate();
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        // nu e în trecut, dar nici mai departe de o lună
        assertEquals(true, upcoming >= startOfToday.getTimeInMillis());
        startOfToday.add(Calendar.MONTH, 1);
        assertEquals(true, upcoming <= startOfToday.getTimeInMillis());
    }
}
