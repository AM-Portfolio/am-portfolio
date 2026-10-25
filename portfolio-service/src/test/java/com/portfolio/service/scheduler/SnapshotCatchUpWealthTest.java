package com.portfolio.service.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;

class SnapshotCatchUpWealthTest {

    @Test
    void resolveHoldingPriceSkipsMissingWhenAvgBuyZero() {
        assertTrue(SnapshotCatchUpService.resolveHoldingPrice(null, 0.0).isEmpty());
        assertTrue(SnapshotCatchUpService.resolveHoldingPrice(0.0, 0.0).isEmpty());
        assertTrue(SnapshotCatchUpService.resolveHoldingPrice(-1.0, 0.0).isEmpty());
    }

    @Test
    void resolveHoldingPricePrefersMarketClose() {
        Optional<Double> p = SnapshotCatchUpService.resolveHoldingPrice(250.5, 100.0);
        assertTrue(p.isPresent());
        assertEquals(250.5, p.get(), 1e-9);
    }

    @Test
    void resolveHoldingPriceFallsBackToPositiveAvgBuy() {
        Optional<Double> p = SnapshotCatchUpService.resolveHoldingPrice(null, 120.0);
        assertTrue(p.isPresent());
        assertEquals(120.0, p.get(), 1e-9);
    }

    @Test
    void buildEntriesSkipsUnpricedHoldingsWithZeroAvgBuy() {
        SnapshotCatchUpService svc = newCatchUpStub();
        Map<String, List<SnapshotCatchUpService.HoldingInfo>> holdings = new HashMap<>();
        holdings.put("dhan-id", List.of(
                new SnapshotCatchUpService.HoldingInfo(
                        "RELIANCE", 10, 0.0, "DHAN", "Dhan", null, null),
                new SnapshotCatchUpService.HoldingInfo(
                        "TCS", 5, 0.0, "DHAN", "Dhan", null, null)));

        Map<String, Double> prices = Map.of("RELIANCE", 100.0);
        List<PortfolioSnapshotEntry> entries = svc.buildEntriesForDate(
                holdings, prices, LocalDate.of(2026, 9, 1));

        assertEquals(1, entries.size());
        assertEquals(1000.0, entries.get(0).getClose(), 1e-9);
        assertEquals(1, entries.get(0).getHoldings().size());
        assertEquals("RELIANCE", entries.get(0).getHoldings().get(0).getSymbol());
    }

    @Test
    void buildEntriesOmitsPortfolioWhenNoPricedHoldings() {
        SnapshotCatchUpService svc = newCatchUpStub();
        Map<String, List<SnapshotCatchUpService.HoldingInfo>> holdings = Map.of(
                "dhan-id", List.of(new SnapshotCatchUpService.HoldingInfo(
                        "RELIANCE", 10, 0.0, "DHAN", "Dhan", null, null)));

        List<PortfolioSnapshotEntry> entries = svc.buildEntriesForDate(
                holdings, Map.of(), LocalDate.of(2026, 9, 1));
        assertTrue(entries.isEmpty());
    }

    @Test
    void buildEntriesValuesAllBrokersIndependently() {
        SnapshotCatchUpService svc = newCatchUpStub();
        Map<String, List<SnapshotCatchUpService.HoldingInfo>> holdings = new HashMap<>();
        holdings.put("zerodha-id", List.of(new SnapshotCatchUpService.HoldingInfo(
                "INFY", 2, 50.0, "ZERODHA", "Zerodha", null, null)));
        holdings.put("dhan-id", List.of(new SnapshotCatchUpService.HoldingInfo(
                "TCS", 1, 0.0, "DHAN", "Dhan", null, null)));

        Map<String, Double> prices = Map.of("INFY", 200.0, "TCS", 3000.0);
        List<PortfolioSnapshotEntry> entries = svc.buildEntriesForDate(
                holdings, prices, LocalDate.of(2026, 9, 1));

        assertEquals(2, entries.size());
        double total = entries.stream().mapToDouble(e -> e.getClose() != null ? e.getClose() : 0).sum();
        assertEquals(2 * 200.0 + 3000.0, total, 1e-9);
        assertFalse(entries.stream().anyMatch(e -> e.getClose() == null || e.getClose() <= 0));
    }

    /** Minimal instance — buildEntriesForDate does not use injected deps. */
    private static SnapshotCatchUpService newCatchUpStub() {
        return new SnapshotCatchUpService(null, null, null);
    }
}
