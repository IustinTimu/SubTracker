package com.example.proiectcmo.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proiectcmo.model.AiFinding;

import org.junit.Test;

public class AiFindingParserTest {
    @Test public void parsesFullFinding() throws Exception {
        String json = "{\"subscriptions\":[{\"name\":\"Calm\",\"vendor\":\"Apple\",\"price\":49.99,\"currency\":\"RON\",\"billingCycle\":\"MONTHLY\",\"lastChargeDate\":\"2026-11-03\",\"nextPaymentDate\":\"2026-12-03\",\"status\":\"ACTIVE\",\"evidenceIds\":[\"m1\"],\"matchesExistingId\":\"\",\"reason\":\"chitanță din 3 nov.\"}]}";
        AiFinding f = AiFindingParser.parse(json).get(0);
        assertEquals("Calm", f.name);
        assertEquals(49.99, f.price, 0.001);
        assertEquals(AiFinding.Status.ACTIVE, f.status);
        assertTrue(f.nextPaymentDate > f.lastChargeDate);
    }

    @Test public void missingOptionalFieldsBecomeEmpty() throws Exception {
        AiFinding f = AiFindingParser.parse("{\"subscriptions\":[{\"name\":\"X\",\"status\":\"UNCERTAIN\"}]}").get(0);
        assertEquals(0, f.nextPaymentDate);
        assertNull(f.matchesExistingId);
        assertTrue(f.evidenceIds.isEmpty());
    }

    @Test(expected = AnalyzerException.class) public void invalidJsonThrows() throws Exception {
        AiFindingParser.parse("nu e json");
    }

    @Test(expected = AnalyzerException.class) public void malformedDateThrowsInvalidResponse() throws Exception {
        AiFindingParser.parse("{\"subscriptions\":[{\"name\":\"X\",\"lastChargeDate\":\"abc\",\"status\":\"ACTIVE\"}]}");
    }
}
