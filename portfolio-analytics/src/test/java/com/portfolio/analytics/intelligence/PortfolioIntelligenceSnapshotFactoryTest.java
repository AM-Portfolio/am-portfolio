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
}
