package com.portfolio.basket.service;

import com.portfolio.basket.model.EtfData;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtfPerformanceEnricherTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private EtfPerformanceEnricher enricher;

    @Test
    void needsPerformanceFill_whenReturnsMissing() {
        EtfData etf = new EtfData();
        etf.setSymbol("ITBEES");
        assertTrue(EtfPerformanceEnricher.needsPerformanceFill(etf));
        etf.setReturn1Y(1.0);
        etf.setReturn3Y(2.0);
        etf.setReturn5Y(3.0);
        etf.setSparklineCloses(List.of(1.0, 2.0));
        assertFalse(EtfPerformanceEnricher.needsPerformanceFill(etf));
    }

    @Test
    void fillMissing_populatesReturnsAndSparklineFromWeeklyHist() {
        EtfData etf = new EtfData();
        etf.setSymbol("ITBEES");

        when(marketDataService.getHistoricalData(any())).thenReturn(Map.of(
                "ITBEES", weeklySeries(100.0, 0.002, 280)));

        int filled = enricher.fillMissing(List.of(etf));

        assertEquals(1, filled);
        assertNotNull(etf.getReturn1Y());
        assertNotNull(etf.getReturn3Y());
        assertNotNull(etf.getReturn5Y());
        assertTrue(etf.getSparklineCloses().size() >= 2);
        assertNotNull(etf.getReturnsAsOf());

        ArgumentCaptor<HistoricalDataRequest> cap = ArgumentCaptor.forClass(HistoricalDataRequest.class);
        verify(marketDataService).getHistoricalData(cap.capture());
        assertEquals("1W", cap.getValue().getInterval());
        assertEquals("ALL", cap.getValue().getFilterType());
    }

    @Test
    void fillMissing_preservesParserReturns() {
        EtfData etf = new EtfData();
        etf.setSymbol("NIFTYBEES");
        // Already normalized 2dp + spark → no hist call needed
        etf.setReturn1Y(11.1);
        etf.setReturn3Y(22.2);
        etf.setReturn5Y(33.3);
        etf.setSparklineCloses(List.of(100.0, 110.0));

        int filled = enricher.fillMissing(List.of(etf));

        assertEquals(0, filled);
        verify(marketDataService, never()).getHistoricalData(any());
        assertEquals(11.1, etf.getReturn1Y());
    }

    @Test
    void fillMissing_overwritesHighPrecisionParserTotalsWithCagr() {
        EtfData etf = new EtfData();
        etf.setSymbol("NIFTYBEES");
        etf.setReturn1Y(-4.4564);
        etf.setReturn3Y(22.3112);
        etf.setReturn5Y(38.6804);

        when(marketDataService.getHistoricalData(any())).thenReturn(Map.of(
                "NIFTYBEES", weeklySeries(100.0, 0.001, 280)));

        enricher.fillMissing(List.of(etf));

        assertNotNull(etf.getReturn1Y());
        assertNotNull(etf.getReturn3Y());
        assertNotNull(etf.getReturn5Y());
        assertFalse(EtfPerformanceEnricher.hasExcessPrecision(etf.getReturn1Y()));
        assertFalse(EtfPerformanceEnricher.hasExcessPrecision(etf.getReturn3Y()));
    }

    @Test
    void fillMissing_shortSeriesUsesInceptionFallbackWhenCoverageOk() {
        EtfData etf = new EtfData();
        etf.setSymbol("METALIETF");
        // ~2.5 years of weekly data → enough for 3Y at 70% coverage, not for 5Y
        when(marketDataService.getHistoricalData(any())).thenReturn(Map.of(
                "METALIETF", weeklySeries(100.0, 0.002, 130)));

        enricher.fillMissing(List.of(etf));

        assertNotNull(etf.getReturn1Y());
        assertNotNull(etf.getReturn3Y());
        // 130 weeks ≈ 2.5y < 5*0.7 → 5Y may stay null
    }

    @Test
    void fillMissing_clearsStaleHighPrecisionWhenHistCannotSupportPeriod() {
        EtfData etf = new EtfData();
        etf.setSymbol("AUTOBEES");
        etf.setReturn5Y(145.1638);

        when(marketDataService.getHistoricalData(any())).thenReturn(Map.of(
                "AUTOBEES", weeklySeries(100.0, 0.002, 280)));

        enricher.fillMissing(List.of(etf));

        assertNotNull(etf.getReturn5Y());
        assertTrue(Math.abs(etf.getReturn5Y()) <= 150.0);
        assertFalse(EtfPerformanceEnricher.hasExcessPrecision(etf.getReturn5Y()));
    }

    @Test
    void periodReturn_cagrVsTotal() {
        List<EtfPerformanceEnricher.ClosePoint> points = new ArrayList<>();
        LocalDate end = LocalDate.of(2026, 9, 15);
        // Exactly 3y earlier close 100, last 133.1 → CAGR ~10%
        points.add(new EtfPerformanceEnricher.ClosePoint(
                end.minusYears(3).atStartOfDay(ZONE).toInstant(), 100.0));
        points.add(new EtfPerformanceEnricher.ClosePoint(
                end.atStartOfDay(ZONE).toInstant(), 133.1));
        Double cagr = EtfPerformanceEnricher.periodReturnPct(points, points.get(1), 3, true);
        Double total = EtfPerformanceEnricher.periodReturnPct(points, points.get(1), 3, false);
        assertNotNull(cagr);
        assertNotNull(total);
        assertEquals(10.0, cagr, 0.2);
        assertEquals(33.1, total, 0.2);
    }

    private static MarketData weeklySeries(double startClose, double weeklyReturn, int weeks) {
        List<MarketData.MarketDataPoint> pts = new ArrayList<>();
        LocalDate day = LocalDate.now(ZONE).minusWeeks(weeks);
        double close = startClose;
        for (int i = 0; i <= weeks; i++) {
            pts.add(point(day, close));
            close *= (1.0 + weeklyReturn);
            day = day.plusWeeks(1);
        }
        return MarketData.builder()
                .symbol("X")
                .historical(true)
                .dataPoints(pts)
                .build();
    }

    private static MarketData.MarketDataPoint point(LocalDate day, double close) {
        Instant ts = day.atStartOfDay(ZONE).toInstant();
        return MarketData.MarketDataPoint.builder()
                .timestamp(ts)
                .ohlcData(OhlcData.builder().open(close).high(close).low(close).close(close).build())
                .volume(0)
                .build();
    }
}
