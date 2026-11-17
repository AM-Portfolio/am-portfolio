package com.portfolio.model.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoldingInstrumentClassifierTest {

    @Test
    void infSchemeIsEtfOrScheme_sectorEtf_mfDematIsMutualFundUnlessListedTicker() {
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfOrScheme("INF277KA1976", "INF277KA1976"));
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfSymbol("INF277"));
        assertEquals(
                HoldingInstrumentClassifier.SECTOR_ETF,
                HoldingInstrumentClassifier.resolveSector(null, "INF277KA1976", "INF277KA1976", "LIVE"));
        assertEquals(
                HoldingInstrumentClassifier.PRODUCT_ETF,
                HoldingInstrumentClassifier.productClass("INF277KA1976", "INF277KA1976", "LIVE"));
        assertEquals(
                HoldingInstrumentClassifier.PRODUCT_MUTUAL_FUND,
                HoldingInstrumentClassifier.productClass(
                        "mutual_fund_demat", "INF277KA1976", "INF277KA1976", "LIVE", "Scheme"));
        assertEquals(
                HoldingInstrumentClassifier.PRODUCT_ETF,
                HoldingInstrumentClassifier.productClass(
                        "mutual_fund_demat", "NIFTYBEES", "INF204KB18I3", "LIVE", "Nippon India ETF"));
    }

    @Test
    void bandhanAmcNeverMapsToRecltd_andQuarantinesEquityLtp() {
        assertTrue(HoldingInstrumentClassifier.isUnsafeNameToTickerMap("Bandhan AMC", "RECLTD"));
        assertTrue(HoldingInstrumentClassifier.quarantineEquityLtp(
                "mutual_fund_demat", "RECLTD", "INF109KC1V59", "Bandhan Mutual Fund"));
        assertFalse(HoldingInstrumentClassifier.quarantineEquityLtp(
                "equity", "RECLTD", "INE020B01018", "REC Limited"));
        assertFalse(HoldingInstrumentClassifier.quarantineEquityLtp(
                "mutual_fund_demat", "ITBEES", "INF204KB15V2", "Nippon India ETF Nifty IT"));
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
        assertFalse(HoldingInstrumentClassifier.isUnsafeNameToTickerMap("Nippon India ETF", "NIFTYBEES"));
    }

    @Test
    void missingSectorTreatsDashAndUnknown() {
        assertTrue(HoldingInstrumentClassifier.isMissingSector("-"));
        assertTrue(HoldingInstrumentClassifier.isMissingSector("Unknown"));
        assertTrue(HoldingInstrumentClassifier.isMissingSector(null));
        assertFalse(HoldingInstrumentClassifier.isMissingSector("Information Technology"));
    }

    @Test
    void suggestDisplayTickerFromBrokerEtfName() {
        assertEquals(
                "TATAGOLD",
                HoldingInstrumentClassifier.suggestDisplayTickerFromName("TATAAML-TATAGOLD"));
        assertEquals(
                "JIOPLATFORMS",
                HoldingInstrumentClassifier.suggestDisplayTickerFromName("JIO PLATFORMS LIMITED"));
        assertNull(HoldingInstrumentClassifier.suggestDisplayTickerFromName("INF277KA1976"));
        assertNull(HoldingInstrumentClassifier.suggestDisplayTickerFromName(null));
    }
}
