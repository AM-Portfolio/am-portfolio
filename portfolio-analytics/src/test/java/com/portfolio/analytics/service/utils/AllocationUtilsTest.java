package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AllocationUtilsTest {

    private MarketData buildMarketData(double lastPrice) {
        MarketData md = new MarketData();
        md.setLastPrice(lastPrice);
        return md;
    }

    // --- calculateMarketCaps ---

    @Test
    void calculateMarketCaps_withValidData_returnsMarketCaps() {
        Map<String, MarketData> data = Map.of(
                "AAPL", buildMarketData(150.0),
                "GOOG", buildMarketData(200.0));

        Map<String, Double> caps = AllocationUtils.calculateMarketCaps(data);

        assertEquals(2, caps.size());
        assertEquals(150_000_000.0, caps.get("AAPL"));
        assertEquals(200_000_000.0, caps.get("GOOG"));
    }

    @Test
    void calculateMarketCaps_withZeroPrice_skipsStock() {
        Map<String, MarketData> data = Map.of("ZERO", buildMarketData(0.0));

        Map<String, Double> caps = AllocationUtils.calculateMarketCaps(data);

        assertTrue(caps.isEmpty());
    }

    @Test
    void calculateMarketCaps_withNullData_skipsStock() {
        Map<String, MarketData> data = new HashMap<>();
        data.put("NULL", null);

        Map<String, Double> caps = AllocationUtils.calculateMarketCaps(data);

        assertTrue(caps.isEmpty());
    }

    @Test
    void calculateMarketCaps_emptyMap_returnsEmpty() {
        Map<String, Double> caps = AllocationUtils.calculateMarketCaps(Collections.emptyMap());
        assertTrue(caps.isEmpty());
    }

    // --- calculateMarketValues ---

    @Test
    void calculateMarketValues_withValidData_returnsValues() {
        Map<String, MarketData> marketData = Map.of(
                "AAPL", buildMarketData(150.0),
                "GOOG", buildMarketData(200.0));
        Map<String, Double> quantities = Map.of("AAPL", 10.0, "GOOG", 5.0);

        Map<String, Double> values = AllocationUtils.calculateMarketValues(marketData, quantities);

        assertEquals(1500.0, values.get("AAPL"));
        assertEquals(1000.0, values.get("GOOG"));
    }

    @Test
    void calculateMarketValues_missingMarketData_skipsStock() {
        Map<String, MarketData> marketData = Map.of("AAPL", buildMarketData(150.0));
        Map<String, Double> quantities = Map.of("AAPL", 10.0, "MISSING", 5.0);

        Map<String, Double> values = AllocationUtils.calculateMarketValues(marketData, quantities);

        assertEquals(1, values.size());
        assertNull(values.get("MISSING"));
    }

    // --- calculateTotalValue ---

    @Test
    void calculateTotalValue_withValues_returnsSumRounded() {
        Map<String, Double> values = Map.of("A", 100.123, "B", 200.456);
        double total = AllocationUtils.calculateTotalValue(values);
        assertEquals(300.58, total);
    }

    @Test
    void calculateTotalValue_emptyMap_returnsZero() {
        assertEquals(0.0, AllocationUtils.calculateTotalValue(Collections.emptyMap()));
    }

    // --- calculateSectorWeights ---

    @Test
    void calculateSectorWeights_withValidData_returnsSortedWeights() {
        Map<String, List<String>> sectorToStocks = new LinkedHashMap<>();
        sectorToStocks.put("Tech", List.of("AAPL", "GOOG"));
        sectorToStocks.put("Finance", List.of("JPM"));

        Map<String, Double> stockToValue = Map.of("AAPL", 300.0, "GOOG", 200.0, "JPM", 500.0);
        double totalValue = 1000.0;

        List<SectorAllocation.SectorWeight> weights =
                AllocationUtils.calculateSectorWeights(sectorToStocks, stockToValue, totalValue);

        assertEquals(2, weights.size());
        // Both sectors are 50%, verify both are present
        var names = weights.stream().map(SectorAllocation.SectorWeight::getSectorName).toList();
        assertTrue(names.contains("Finance"));
        assertTrue(names.contains("Tech"));
        assertEquals(50.0, weights.get(0).getWeightPercentage());
    }

    @Test
    void calculateSectorWeights_zeroTotal_returnsZeroPercent() {
        Map<String, List<String>> sectorToStocks = Map.of("Tech", List.of("AAPL"));
        Map<String, Double> stockToValue = Map.of("AAPL", 100.0);

        List<SectorAllocation.SectorWeight> weights =
                AllocationUtils.calculateSectorWeights(sectorToStocks, stockToValue, 0.0);

        assertEquals(0.0, weights.get(0).getWeightPercentage());
    }

    // --- calculateIndustryWeights ---

    @Test
    void calculateIndustryWeights_withValidData_returnsWeights() {
        Map<String, List<String>> industryToStocks = Map.of("Software", List.of("AAPL"));
        Map<String, String> industryToSector = Map.of("Software", "Tech");
        Map<String, Double> stockToValue = Map.of("AAPL", 500.0);

        List<SectorAllocation.IndustryWeight> weights =
                AllocationUtils.calculateIndustryWeights(industryToStocks, industryToSector, stockToValue, 1000.0);

        assertEquals(1, weights.size());
        assertEquals("Software", weights.get(0).getIndustryName());
        assertEquals("Tech", weights.get(0).getParentSector());
        assertEquals(50.0, weights.get(0).getWeightPercentage());
    }

    @Test
    void calculateIndustryWeights_missingSector_defaultsToOther() {
        Map<String, List<String>> industryToStocks = Map.of("Unknown", List.of("XYZ"));
        Map<String, String> industryToSector = Collections.emptyMap(); // no mapping
        Map<String, Double> stockToValue = Map.of("XYZ", 100.0);

        List<SectorAllocation.IndustryWeight> weights =
                AllocationUtils.calculateIndustryWeights(industryToStocks, industryToSector, stockToValue, 1000.0);

        assertEquals("Other", weights.get(0).getParentSector());
    }

    // --- calculateGroupValue ---

    @Test
    void calculateGroupValue_returnsSum() {
        List<String> stocks = List.of("A", "B", "C");
        Map<String, Double> values = Map.of("A", 10.0, "B", 20.0, "C", 30.0);
        assertEquals(60.0, AllocationUtils.calculateGroupValue(stocks, values));
    }

    @Test
    void calculateGroupValue_missingStocksDefaultToZero() {
        List<String> stocks = List.of("A", "MISSING");
        Map<String, Double> values = Map.of("A", 10.0);
        assertEquals(10.0, AllocationUtils.calculateGroupValue(stocks, values));
    }

    // --- calculateWeightPercentage ---

    @Test
    void calculateWeightPercentage_normalCase() {
        assertEquals(25.0, AllocationUtils.calculateWeightPercentage(250.0, 1000.0));
    }

    @Test
    void calculateWeightPercentage_zeroTotal_returnsZero() {
        assertEquals(0.0, AllocationUtils.calculateWeightPercentage(100.0, 0.0));
    }

    // --- getTopStocksByValue ---

    @Test
    void getTopStocksByValue_returnsTopNSorted() {
        List<String> stocks = List.of("A", "B", "C", "D");
        Map<String, Double> values = Map.of("A", 10.0, "B", 40.0, "C", 30.0, "D", 20.0);

        List<String> top = AllocationUtils.getTopStocksByValue(stocks, values, 2);

        assertEquals(2, top.size());
        assertEquals("B", top.get(0));
        assertEquals("C", top.get(1));
    }

    // --- roundToTwoDecimals ---

    @Test
    void roundToTwoDecimals_roundsCorrectly() {
        assertEquals(3.15, AllocationUtils.roundToTwoDecimals(3.145));
        assertEquals(3.15, AllocationUtils.roundToTwoDecimals(3.1450001));
        assertEquals(0.0, AllocationUtils.roundToTwoDecimals(0.0));
        assertEquals(-1.23, AllocationUtils.roundToTwoDecimals(-1.234));
    }
}
