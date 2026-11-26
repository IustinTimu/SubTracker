package com.example.proiectcmo.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnalyzerExceptionTest {
    @Test
    public void onlyOverloadCountsAsBusy() {
        assertTrue(AnalyzerException.isBusy("This model is currently experiencing high demand."));
        assertTrue(AnalyzerException.isBusy("Deadline expired before operation could complete."));
        assertFalse(AnalyzerException.isBusy("models/x is not found for API version v1beta"));
    }
}
