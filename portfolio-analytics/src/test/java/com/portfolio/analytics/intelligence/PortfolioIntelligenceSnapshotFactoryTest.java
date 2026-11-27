package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.HoldingInstrumentClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioIntelligenceSnapshotFactoryTest {

    @Test
    void usableMeta_rejectsBlankDashAndUnknown() {
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta(null));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta(""));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("  "));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("-"));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("Unknown"));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("unknown"));
        assertEquals("Financial Services",
                PortfolioIntelligenceSnapshotFactory.usableMeta(" Financial Services "));
    }

    @Test
    void needsPointSymbolLookup_skipsTickers() {
        assertFalse(PortfolioIntelligenceSnapshotFactory.needsPointSymbolLookup("RELIANCE"));
        assertTrue(PortfolioIntelligenceSnapshotFactory.needsPointSymbolLookup("INE002A01018"));
        assertTrue(PortfolioIntelligenceSnapshotFactory.needsPointSymbolLookup(null));
    }

    @Test
    void bookPriceFallback_prefersLastMarketPriceOverCost() {
        EquityModel live = EquityModel.builder()
                .symbol("TCS")
                .quantity(2.0)
                .avgBuyingPrice(100.0)
                .currentPrice(250.0)
                .build();
        assertEquals(250.0, PortfolioIntelligenceSnapshotFactory.bookPriceFallback(live));
    }

    @Test
    void bookPriceFallback_usesImportMarkWhenNoAvg() {
        EquityModel markOnly = EquityModel.builder()
                .symbol("JIOFIN")
                .importMarkPrice(250.0)
                .build();
        assertEquals(250.0, PortfolioIntelligenceSnapshotFactory.bookPriceFallback(markOnly));
    }

    @Test
    void resolveSector_infSchemeMapsToEtfNotUnknown() {
        EquityModel gold = EquityModel.builder()
                .symbol("GOLDBEES")
                .isin("INF277KA1976")
                .build();
        String sector = PortfolioIntelligenceSnapshotFactory.resolveSector(
                gold, null, "GOLDBEES", HoldingInstrumentClassifier.PRICE_COST);
        assertEquals("Exchange Traded Funds (ETFs)", sector);
    }

    @Test
    void resolveSector_listedEtfBeatsStickyFinancialServices() {
        EquityModel nifty = EquityModel.builder()
                .symbol("NIFTYBEES")
                .sector("Financial Services")
                .name("NIPPON INDIA ETF NIFTY BEES")
                .build();
        String sector = PortfolioIntelligenceSnapshotFactory.resolveSector(
                nifty, null, "NIFTYBEES", HoldingInstrumentClassifier.PRICE_LIVE);
        assertEquals(HoldingInstrumentClassifier.SECTOR_ETF, sector);
    }

    @Test
    void resolveSector_listedEtfBeatsStickyMutualFundsBucket() {
        EquityModel small = EquityModel.builder()
                .symbol("MOSMALL250")
                .sector("Mutual Funds")
                .name("MOTILAL OSWAL NIFTY SMALLCAP 250 ETF")
                .assetClass("mutual_fund_demat")
                .build();
        String sector = PortfolioIntelligenceSnapshotFactory.resolveSector(
                small, null, "MOSMALL250", HoldingInstrumentClassifier.PRICE_LIVE);
        assertEquals(HoldingInstrumentClassifier.SECTOR_ETF, sector);
    }

    @Test
    void resolveSector_keepsListedEquitySectorFromDoc() {
        EquityModel tcs = EquityModel.builder()
                .symbol("TCS")
                .isin("INE467B01029")
                .sector("Information Technology")
                .build();
        String sector = PortfolioIntelligenceSnapshotFactory.resolveSector(
                tcs, null, "TCS", HoldingInstrumentClassifier.PRICE_COST);
        assertEquals("Information Technology", sector);
    }
}
