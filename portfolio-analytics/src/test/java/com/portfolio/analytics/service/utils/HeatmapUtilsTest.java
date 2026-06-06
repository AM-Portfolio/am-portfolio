package com.portfolio.analytics.service.utils;

import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HeatmapUtilsTest {

    private MarketData md(double last, double open, double close) {
        MarketData m = new MarketData();
        m.setLastPrice(last);
        m.setOhlc(OhlcData.builder().open(open).close(close).build());
        return m;
    }

    @Test void sectorMetrics_singleStock() {
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of(md(110, 100, 105)));
        assertTrue(metrics.getChangePercent() > 0);
        assertTrue(metrics.getPerformance() > 0);
    }

    @Test void sectorMetrics_emptyList() {
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of());
        assertEquals(0.0, metrics.getPerformance());
        assertEquals(0.0, metrics.getChangePercent());
    }

    @Test void sectorMetrics_zeroPricesSkipped() {
        MarketData m = new MarketData();
        m.setLastPrice(100.0);
        m.setOhlc(OhlcData.builder().open(0).close(0).build());
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of(m));
        assertEquals(0.0, metrics.getPerformance());
    }

    @Test void sectorMetrics_nullOhlcSkipped() {
        MarketData m = new MarketData();
        m.setLastPrice(100.0);
        m.setOhlc(null);
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of(m));
        assertEquals(0.0, metrics.getPerformance());
    }

    @Test void sectorMetrics_withWeightage() {
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of(md(110, 100, 105)), 1000.0, 500.0);
        assertEquals(50.0, metrics.getWeightage());
    }

    @Test void sectorMetrics_nullTotalValue() {
        var metrics = HeatmapUtils.calculateSectorMetrics(List.of(md(110, 100, 105)), null, null);
        assertEquals(0.0, metrics.getWeightage());
    }

    @Test void weightedSectorMetrics_basic() {
        var metrics = HeatmapUtils.calculateWeightedSectorMetrics(
                List.of(md(110, 100, 105)), List.of(10.0));
        assertTrue(metrics.getPerformance() > 0);
    }

    @Test void weightedSectorMetrics_withPortfolioValue() {
        var metrics = HeatmapUtils.calculateWeightedSectorMetrics(
                List.of(md(100, 100, 100)), List.of(10.0), 2000.0);
        assertEquals(50.0, metrics.getWeightage());
    }

    @Test void weightedSectorMetrics_nullPortfolioValue() {
        var metrics = HeatmapUtils.calculateWeightedSectorMetrics(
                List.of(md(100, 100, 100)), List.of(10.0), null);
        assertEquals(0.0, metrics.getWeightage());
    }

    @Test void weightedSectorMetrics_zeroOpenPrice() {
        var metrics = HeatmapUtils.calculateWeightedSectorMetrics(
                List.of(md(100, 0, 100)), List.of(10.0));
        assertEquals(0.0, metrics.getChangePercent());
    }

    @Test void sectorMetricsConstructor_twoArg() {
        var m = new HeatmapUtils.SectorMetrics(1.234, 5.678);
        assertEquals(1.23, m.getPerformance());
        assertEquals(5.68, m.getChangePercent());
        assertEquals(0.0, m.getWeightage());
    }

    @Test void convertToStockDetail_nullData() {
        assertNull(HeatmapUtils.convertToStockDetail("SYM", null, 10));
    }

    @Test void convertToStockDetail_nullOhlc() {
        MarketData m = new MarketData();
        m.setLastPrice(100.0);
        m.setOhlc(null);
        assertNull(HeatmapUtils.convertToStockDetail("SYM", m, 10));
    }

    @Test void convertToStockDetail_valid() {
        var detail = HeatmapUtils.convertToStockDetail("SYM", md(110, 100, 105), 10);
        assertNotNull(detail);
        assertEquals("SYM", detail.getSymbol());
    }

    @Test void createStockDetails_filtersNulls() {
        MarketData nullOhlc = new MarketData();
        nullOhlc.setLastPrice(100.0);
        nullOhlc.setOhlc(null);

        var details = HeatmapUtils.createStockDetails(
                List.of(md(110, 100, 105), nullOhlc),
                List.of(10.0, 5.0),
                List.of("A", "B"));
        assertEquals(1, details.size());
    }

    @Test void createSectorPerformance_basic() {
        var m = new HeatmapUtils.SectorMetrics(2.5, 1.5, 30.0);
        var sp = HeatmapUtils.createSectorPerformance("Technology", m);
        assertEquals("Technology", sp.getSectorName());
        assertEquals("TECH", sp.getSectorCode());
    }

    @Test void createSectorPerformance_nullName() {
        var m = new HeatmapUtils.SectorMetrics(0, 0);
        var sp = HeatmapUtils.createSectorPerformance(null, m);
        assertEquals("UNKN", sp.getSectorCode());
    }

    @Test void createSectorPerformance_shortName() {
        var m = new HeatmapUtils.SectorMetrics(0, 0);
        var sp = HeatmapUtils.createSectorPerformance("IT", m);
        assertEquals("IT", sp.getSectorCode());
    }

    @Test void createSectorPerformance_emptyName() {
        var m = new HeatmapUtils.SectorMetrics(0, 0);
        var sp = HeatmapUtils.createSectorPerformance("", m);
        assertEquals("UNKN", sp.getSectorCode());
    }

    @Test void createSectorPerformance_withCode() {
        var m = new HeatmapUtils.SectorMetrics(1.0, 2.0, 3.0);
        var sp = HeatmapUtils.createSectorPerformance("Finance", "FIN", m);
        assertEquals("FIN", sp.getSectorCode());
    }

    @Test void roundToTwoDecimals() {
        assertEquals(3.15, HeatmapUtils.roundToTwoDecimals(3.145));
    }
}
