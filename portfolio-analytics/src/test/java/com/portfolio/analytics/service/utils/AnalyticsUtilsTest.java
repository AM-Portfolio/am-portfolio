package com.portfolio.analytics.service.utils;

import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsUtilsTest {

    private MarketData buildMarketData(double lastPrice, long instrumentToken) {
        MarketData md = new MarketData();
        md.setLastPrice(lastPrice);
        md.setInstrumentToken(instrumentToken);
        return md;
    }

    // --- calculateMarketCap ---

    @Test
    void calculateMarketCap_returnsProductOfPriceAndMultiplier() {
        MarketData md = buildMarketData(100.0, 0);
        double result = AnalyticsUtils.calculateMarketCap(md, 1_000_000.0);
        assertEquals(100_000_000.0, result);
    }

    @Test
    void calculateMarketCap_withZeroPrice_returnsZero() {
        MarketData md = buildMarketData(0.0, 0);
        assertEquals(0.0, AnalyticsUtils.calculateMarketCap(md, 1_000_000.0));
    }

    // --- findSymbolByInstrumentToken ---

    @Test
    void findSymbolByInstrumentToken_findsMatch() {
        Map<String, MarketData> data = Map.of(
                "AAPL", buildMarketData(150.0, 123),
                "GOOG", buildMarketData(200.0, 456));

        assertEquals("AAPL", AnalyticsUtils.findSymbolByInstrumentToken(123, data));
    }

    @Test
    void findSymbolByInstrumentToken_noMatch_returnsNull() {
        Map<String, MarketData> data = Map.of("AAPL", buildMarketData(150.0, 123));
        assertNull(AnalyticsUtils.findSymbolByInstrumentToken(999, data));
    }

    // --- getTopEntriesByValue ---

    @Test
    void getTopEntriesByValue_descending_returnsHighestFirst() {
        Map<String, Double> map = Map.of("A", 10.0, "B", 40.0, "C", 30.0, "D", 20.0);

        List<String> top = AnalyticsUtils.getTopEntriesByValue(map, 2, true);

        assertEquals(2, top.size());
        assertEquals("B", top.get(0));
        assertEquals("C", top.get(1));
    }

    @Test
    void getTopEntriesByValue_ascending_returnsLowestFirst() {
        Map<String, Double> map = Map.of("A", 10.0, "B", 40.0, "C", 30.0);

        List<String> top = AnalyticsUtils.getTopEntriesByValue(map, 2, false);

        assertEquals(2, top.size());
        assertEquals("A", top.get(0));
    }

    @Test
    void getTopEntriesByValue_limitExceedsSize_returnsAll() {
        Map<String, Integer> map = Map.of("A", 1, "B", 2);
        List<String> result = AnalyticsUtils.getTopEntriesByValue(map, 10, true);
        assertEquals(2, result.size());
    }

    // --- calculateWeightedAverage ---

    @Test
    void calculateWeightedAverage_normalCase() {
        List<Double> values = List.of(10.0, 20.0, 30.0);
        List<Double> weights = List.of(1.0, 2.0, 3.0);

        // (10*1 + 20*2 + 30*3) / (1+2+3) = (10+40+90)/6 = 140/6 ≈ 23.33
        double result = AnalyticsUtils.calculateWeightedAverage(values, weights);
        assertEquals(140.0 / 6.0, result, 0.001);
    }

    @Test
    void calculateWeightedAverage_nullValues_returnsZero() {
        assertEquals(0.0, AnalyticsUtils.calculateWeightedAverage(null, List.of(1.0)));
    }

    @Test
    void calculateWeightedAverage_nullWeights_returnsZero() {
        assertEquals(0.0, AnalyticsUtils.calculateWeightedAverage(List.of(1.0), null));
    }

    @Test
    void calculateWeightedAverage_differentSizes_returnsZero() {
        assertEquals(0.0, AnalyticsUtils.calculateWeightedAverage(List.of(1.0, 2.0), List.of(1.0)));
    }

    @Test
    void calculateWeightedAverage_emptyLists_returnsZero() {
        assertEquals(0.0, AnalyticsUtils.calculateWeightedAverage(List.of(), List.of()));
    }

    @Test
    void calculateWeightedAverage_zeroWeights_returnsZero() {
        List<Double> values = List.of(10.0, 20.0);
        List<Double> weights = List.of(0.0, 0.0);
        assertEquals(0.0, AnalyticsUtils.calculateWeightedAverage(values, weights));
    }

    // --- groupMarketDataBy ---

    @Test
    void groupMarketDataBy_groupsCorrectly() {
        MarketData mdA = buildMarketData(100.0, 1);
        MarketData mdB = buildMarketData(200.0, 2);
        MarketData mdC = buildMarketData(300.0, 3);

        Map<String, MarketData> marketData = Map.of("A", mdA, "B", mdB, "C", mdC);
        Map<String, String> symbolToGroup = Map.of("A", "Tech", "B", "Tech", "C", "Finance");

        Map<String, List<MarketData>> grouped = AnalyticsUtils.groupMarketDataBy(marketData, symbolToGroup);

        assertEquals(2, grouped.size());
        assertEquals(2, grouped.get("Tech").size());
        assertEquals(1, grouped.get("Finance").size());
    }

    @Test
    void groupMarketDataBy_symbolNotInGroupMap_isExcluded() {
        MarketData md = buildMarketData(100.0, 1);
        Map<String, MarketData> marketData = Map.of("A", md);
        Map<String, String> symbolToGroup = Collections.emptyMap();

        Map<String, List<MarketData>> grouped = AnalyticsUtils.groupMarketDataBy(marketData, symbolToGroup);

        assertTrue(grouped.isEmpty());
    }
}
