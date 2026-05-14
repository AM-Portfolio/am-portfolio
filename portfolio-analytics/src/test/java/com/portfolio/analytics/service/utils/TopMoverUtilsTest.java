package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TopMoverUtilsTest {

    private MarketData md(double last, double open, double close) {
        MarketData m = new MarketData();
        m.setLastPrice(last);
        m.setOhlc(OhlcData.builder().open(open).close(close).build());
        return m;
    }

    @Test void performanceMetrics_calculates() {
        Map<String, Double> perf = new HashMap<>();
        Map<String, Double> change = new HashMap<>();
        TopMoverUtils.calculatePerformanceMetrics(Map.of("A", md(110, 100, 105)), perf, change);
        assertEquals(1, perf.size());
        assertTrue(perf.get("A") > 0);
        assertTrue(change.get("A") > 0);
    }

    @Test void performanceMetrics_zeroPricesSkipped() {
        Map<String, Double> perf = new HashMap<>();
        Map<String, Double> change = new HashMap<>();
        TopMoverUtils.calculatePerformanceMetrics(Map.of("A", md(100, 0, 0)), perf, change);
        assertTrue(perf.isEmpty());
    }

    @Test void performanceMetrics_nullOhlcSkipped() {
        MarketData m = new MarketData(); m.setLastPrice(100.0); m.setOhlc(null);
        Map<String, Double> perf = new HashMap<>();
        Map<String, Double> change = new HashMap<>();
        TopMoverUtils.calculatePerformanceMetrics(Map.of("A", m), perf, change);
        assertTrue(perf.isEmpty());
    }

    @Test void topGainers_returnsPositiveOnly() {
        Map<String, Double> perf = Map.of("UP", 5.0, "DOWN", -3.0);
        Map<String, Double> change = Map.of("UP", 5.0, "DOWN", -3.0);
        Map<String, MarketData> data = Map.of("UP", md(105, 100, 100), "DOWN", md(97, 100, 100));
        var gainers = TopMoverUtils.getTopGainers(data, perf, change, 10);
        assertEquals(1, gainers.size());
        assertEquals("UP", gainers.get(0).getSymbol());
    }

    @Test void topLosers_returnsNegativeOnly() {
        Map<String, Double> perf = Map.of("UP", 5.0, "DOWN", -3.0);
        Map<String, Double> change = Map.of("UP", 5.0, "DOWN", -3.0);
        Map<String, MarketData> data = Map.of("UP", md(105, 100, 100), "DOWN", md(97, 100, 100));
        var losers = TopMoverUtils.getTopLosers(data, perf, change, 10);
        assertEquals(1, losers.size());
        assertEquals("DOWN", losers.get(0).getSymbol());
    }

    @Test void topGainers_respectsLimit() {
        Map<String, Double> perf = Map.of("A", 10.0, "B", 20.0, "C", 30.0);
        Map<String, Double> change = Map.of("A", 10.0, "B", 20.0, "C", 30.0);
        Map<String, MarketData> data = Map.of("A", md(110,100,100), "B", md(120,100,100), "C", md(130,100,100));
        var gainers = TopMoverUtils.getTopGainers(data, perf, change, 2);
        assertEquals(2, gainers.size());
    }

    @Test void createStockMovements_nullDataSkipped() {
        Map<String, MarketData> data = new HashMap<>();
        data.put("A", null);
        var movements = TopMoverUtils.createStockMovements(List.of("A"), data, Map.of("A", 1.0));
        assertTrue(movements.isEmpty());
    }

    @Test void createStockMovements_valid() {
        var movements = TopMoverUtils.createStockMovements(
                List.of("A"), Map.of("A", md(110, 100, 105)), Map.of("A", 10.0));
        assertEquals(1, movements.size());
        assertEquals(110.0, movements.get(0).getLastPrice());
        assertEquals(10.0, movements.get(0).getChangeAmount());
    }

    @Test void buildTopMoversResponse_basic() {
        Map<String, MarketData> data = Map.of(
                "UP", md(110, 100, 100), "DOWN", md(90, 100, 100));
        GainerLoser result = TopMoverUtils.buildTopMoversResponse(data, 5, "idx1", false, null);
        assertNotNull(result);
        assertFalse(result.getTopGainers().isEmpty());
        assertFalse(result.getTopLosers().isEmpty());
    }

    @Test void buildTopMoversResponse_withSectorInfo() {
        Map<String, MarketData> data = Map.of("UP", md(110, 100, 100));
        Map<String, String> sectors = Map.of("UP", "Tech");
        GainerLoser result = TopMoverUtils.buildTopMoversResponse(data, 5, "p1", true, sectors);
        assertEquals("Tech", result.getTopGainers().get(0).getSector());
    }

    @Test void roundToTwoDecimals() { assertEquals(3.15, TopMoverUtils.roundToTwoDecimals(3.145)); }
}
