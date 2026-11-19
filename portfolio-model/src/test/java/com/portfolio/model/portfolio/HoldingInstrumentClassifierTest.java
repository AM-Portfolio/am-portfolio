package com.portfolio.model.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoldingInstrumentClassifierTest {

    @Test
    void infSchemeIsEtfOrScheme_sectorMf_mfDematIsMutualFundUnlessListedTicker() {
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfOrScheme("INF277KA1976", "INF277KA1976"));
        assertTrue(HoldingInstrumentClassifier.looksLikeEtfSymbol("INF277"));
        assertTrue(HoldingInstrumentClassifier.isSchemeMfIsin("INF277KA1976"));
        assertEquals(
                HoldingInstrumentClassifier.SECTOR_MUTUAL_FUND,
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
        assertEquals(
                HoldingInstrumentClassifier.SECTOR_ETF,
                HoldingInstrumentClassifier.resolveSector(
                        "Financial Services", "NIFTYBEES", "INF204KB14I2", "LIVE", "mutual_fund_demat",
                        "NIPPON INDIA ETF NIFTY 50 BEES"));
    }

    @Test
    void bandhanAmcNeverMapsToRecltd_andQuarantinesEquityLtp() {
        assertTrue(HoldingInstrumentClassifier.isUnsafeNameToTickerMap("Bandhan AMC", "RECLTD"));
        assertTrue(HoldingInstrumentClassifier.mustKeepIsinBookKey(
                "mutual_fund_demat", "RECLTD", "INF194KB1AL4",
                "BANDHAN AMC LTD#BANDHAN MF-BANDHAN SMALL CAP FUND-DIRECT-GROWTH"));
        assertTrue(HoldingInstrumentClassifier.quarantineEquityLtp(
                "mutual_fund_demat", "RECLTD", "INF109KC1V59", "Bandhan Mutual Fund"));
        assertTrue(HoldingInstrumentClassifier.isUnsafeNameToTickerMap(
                "MOTILAL OSWAL AMC LTD#MOTILAL OSWAL MF- MOTILAL OSWAL LARGE CAP FUND-DIRECT-GROWTH",
                "RECLTD"));
        assertFalse(HoldingInstrumentClassifier.quarantineEquityLtp(
                "equity", "RECLTD", "INE020B01018", "REC Limited"));
        assertFalse(HoldingInstrumentClassifier.quarantineEquityLtp(
                "mutual_fund_demat", "ITBEES", "INF204KB15V2", "Nippon India ETF Nifty IT"));
        assertFalse(HoldingInstrumentClassifier.isUnsafeNameToTickerMap(
                "MOTILAL OSWAL AMC LTD#MOTILAL OSWAL MF- MOTILAL OSWAL NASDAQ 100 ETF", "MON100"));
        assertEquals("INF194KB1AL4", HoldingInstrumentClassifier.holdingIdentityKey("RECLTD", "INF194KB1AL4"));
    }

    @Test
    void markResolverPrefersImportMarkForQuarantinedScheme() {
        HoldingMarkPriceResolver.Mark mark = HoldingMarkPriceResolver.tryQuarantineMark(
                "mutual_fund_demat",
                "RECLTD",
                "INF194KB1AL4",
                "BANDHAN AMC LTD#BANDHAN MF-BANDHAN SMALL CAP FUND-DIRECT-GROWTH",
                53.227,
                8_27_290.28,
                15_542.681,
                294.95,
                null,
                null);
        assertNotNull(mark);
        assertTrue(mark.isPresent());
        assertEquals(53.227, mark.price(), 1e-6);
        assertEquals(HoldingInstrumentClassifier.PRICE_IMPORT, mark.priceSource());
        assertNull(HoldingMarkPriceResolver.tryQuarantineMark(
                "equity", "RECLTD", "INE020B01018", "REC Limited",
                null, null, 10.0, 295.0, null, null));
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
