package com.portfolio.model.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoldingInstrumentClassifierTest {

    @Test
    void infSchemeIsEtf() {
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfOrScheme("INF277KA1976", "INF277KA1976"));
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfSymbol("INF277"));
        assertEquals(
                HoldingInstrumentClassifier.SECTOR_ETF,
                HoldingInstrumentClassifier.resolveSector(null, "INF277KA1976", "INF277KA1976", "LIVE"));
        assertEquals(
                HoldingInstrumentClassifier.PRODUCT_ETF,
                HoldingInstrumentClassifier.productClass("INF277KA1976", "INF277KA1976", "LIVE"));
    }

    @Test
    void platformsIsinCostIsUnlisted() {
        assertEquals(
                HoldingInstrumentClassifier.SECTOR_UNLISTED,
                HoldingInstrumentClassifier.resolveSector(
                        "-", "INE0BJO01010", "INE0BJO01010", HoldingInstrumentClassifier.PRICE_COST));
        assertEquals(
                HoldingInstrumentClassifier.PRODUCT_UNLISTED,
                HoldingInstrumentClassifier.productClass(
                        "INE0BJO01010", "INE0BJO01010", HoldingInstrumentClassifier.PRICE_COST));
    }

    @Test
    void neverMapPlatformsNameToJiofin() {
        assertTrue(HoldingInstrumentClassifier.isUnsafeNameToTickerMap("JIO PLATFORMS LIMITED", "JIOFIN"));
        assertFalse(HoldingInstrumentClassifier.isUnsafeNameToTickerMap("WIPRO LTD", "WIPRO"));
    }

    @Test
    void missingSectorTreatsDashAndUnknown() {
        assertTrue(HoldingInstrumentClassifier.isMissingSector("-"));
        assertTrue(HoldingInstrumentClassifier.isMissingSector("Unknown"));
        assertTrue(HoldingInstrumentClassifier.isMissingSector(null));
        assertFalse(HoldingInstrumentClassifier.isMissingSector("Information Technology"));
    }
}
