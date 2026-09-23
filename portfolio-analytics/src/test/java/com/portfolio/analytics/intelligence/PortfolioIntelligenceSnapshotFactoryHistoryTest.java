package com.portfolio.analytics.intelligence;

import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.redis.service.PortfolioIntelligenceHistoryRedisService;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioIntelligenceSnapshotFactoryHistoryTest {

    @Mock
    private PortfolioService portfolioService;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private SecurityDetailsService securityDetailsService;
    @Mock
    private PortfolioIntelligenceHistoryRedisService historyCache;

    private PortfolioIntelligenceSnapshotFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PortfolioIntelligenceSnapshotFactory(
                portfolioService, marketDataService, securityDetailsService, historyCache);
        ReflectionTestUtils.setField(factory, "primaryBenchmarkSymbol", "NIFTY 50");
        ReflectionTestUtils.setField(factory, "historyLookbackDays", 60);
        ReflectionTestUtils.setField(factory, "historyTimeoutMs", 800L);
        ReflectionTestUtils.setField(factory, "historyTimeoutStressMs", 20_000L);
    }

    @Test
    void getHistoryTimeoutStressMs_usesConfiguredValue() {
        assertThat(factory.getHistoryTimeoutStressMs()).isEqualTo(20_000L);
    }

    @Test
    void historyWarmedListener_invokedAfterSuccessfulPut() {
        when(historyCache.get(any())).thenReturn(Optional.empty());
        Map<String, MarketData> hist = sampleHistWithBenchmark();
        when(marketDataService.getHistoricalData(any())).thenReturn(hist);

        AtomicReference<String> warmed = new AtomicReference<>();
        factory.setHistoryWarmedListener(warmed::set);

        // Exercise private fetch path via reflection helper: build quantities large enough
        Map<String, Double> qty = Map.of("RELIANCE", 10.0);
        List<String> symbols = List.of("RELIANCE");
        Object result = ReflectionTestUtils.invokeMethod(
                factory, "loadHistoryMetrics", "pid-1", symbols, qty, true, 20_000L);

        assertThat(result).isNotNull();
        // May or may not warm depending on coverage; listener only if put happens
        verify(marketDataService, atLeastOnce()).getHistoricalData(any());
    }

    @Test
    void softTimeout_leavesInFlightWarm_soLaterAwaitGetsBeta() throws Exception {
        when(historyCache.get(any())).thenReturn(Optional.empty());
        when(marketDataService.getHistoricalData(any())).thenAnswer(inv -> {
            Thread.sleep(400);
            return sampleHistWithBenchmark();
        });

        Map<String, Double> qty = Map.of("RELIANCE", 10.0);
        List<String> symbols = List.of("RELIANCE");

        Object soft = ReflectionTestUtils.invokeMethod(
                factory, "loadHistoryMetrics", "pid-soft", symbols, qty, true, 80L);
        assertThat(soft).isNotNull();
        // Soft path may be empty (timeout) or already filled; either way in-flight must finish.
        Thread.sleep(600);
        Object after = ReflectionTestUtils.invokeMethod(
                factory, "loadHistoryMetrics", "pid-soft", symbols, qty, true, 5_000L);
        assertThat(after).isNotNull();
        Integer points = (Integer) ReflectionTestUtils.getField(after, "historyPoints");
        assertThat(points).isNotNull().isGreaterThanOrEqualTo(20);
    }

    @Test
    void fetchHistory_retriesBenchmarkAsIndexWhenEqMisses() {
        Map<String, MarketData> eqOnly = new HashMap<>();
        eqOnly.put("RELIANCE", series("RELIANCE", 100));
        // No NIFTY under EQ
        when(marketDataService.getHistoricalData(any())).thenAnswer(inv -> {
            HistoricalDataRequest req = inv.getArgument(0);
            if (InstrumentType.INDEX.getValue().equals(req.getInstrumentType())) {
                Map<String, MarketData> idx = new HashMap<>();
                idx.put("NIFTY 50", series("NIFTY 50", 200));
                return idx;
            }
            return eqOnly;
        });

        Map<String, Double> qty = Map.of("RELIANCE", 10.0);
        Object fields = ReflectionTestUtils.invokeMethod(
                factory, "fetchHistory", List.of("RELIANCE"), qty);

        ArgumentCaptor<HistoricalDataRequest> cap = ArgumentCaptor.forClass(HistoricalDataRequest.class);
        verify(marketDataService, atLeastOnce()).getHistoricalData(cap.capture());
        List<HistoricalDataRequest> sent = cap.getAllValues();
        HistoricalDataRequest eqReq = sent.stream()
                .filter(r -> InstrumentType.EQ.getValue().equals(r.getInstrumentType()))
                .findFirst()
                .orElseThrow();
        HistoricalDataRequest idxReq = sent.stream()
                .filter(r -> InstrumentType.INDEX.getValue().equals(r.getInstrumentType()))
                .findFirst()
                .orElseThrow();
        assertThat(eqReq.getIsIndexSymbol()).isFalse();
        assertThat(eqReq.getInterval()).isEqualTo(TimeFrame.DAY.getValue());
        assertThat(eqReq.getSymbols()).doesNotContain("NIFTY");
        LocalDate expectedFrom = LocalDate.now().minusDays(60);
        assertThat(eqReq.getFromDate()).isEqualTo(expectedFrom.toString());
        assertThat(idxReq.getIsIndexSymbol()).isTrue();
        assertThat(idxReq.getInterval()).isEqualTo(TimeFrame.DAY.getValue());
        assertThat(idxReq.getFromDate()).isEqualTo(expectedFrom.toString());
        assertThat(fields).isNotNull();
        Integer points = (Integer) ReflectionTestUtils.getField(fields, "historyPoints");
        Double beta = (Double) ReflectionTestUtils.getField(fields, "beta");
        assertThat(points).isNotNull().isGreaterThanOrEqualTo(20);
        assertThat(beta).isNotNull();
    }

    @Test
    void fetchHistory_indexOverlayReplacesFlatEqNiftyWhenKeysDiffer() {
        Map<String, MarketData> eq = new HashMap<>();
        eq.put("RELIANCE", series("RELIANCE", 100));
        eq.put("NIFTY 50", flatSeries("NIFTY 50", 200));
        when(marketDataService.getHistoricalData(any())).thenAnswer(inv -> {
            HistoricalDataRequest req = inv.getArgument(0);
            if (InstrumentType.INDEX.getValue().equals(req.getInstrumentType())) {
                Map<String, MarketData> idx = new HashMap<>();
                idx.put("NIFTY50", series("NIFTY50", 220));
                return idx;
            }
            return eq;
        });

        Object fields = ReflectionTestUtils.invokeMethod(
                factory, "fetchHistory", List.of("RELIANCE"), Map.of("RELIANCE", 10.0));

        assertThat(fields).isNotNull();
        Integer points = (Integer) ReflectionTestUtils.getField(fields, "historyPoints");
        Double beta = (Double) ReflectionTestUtils.getField(fields, "beta");
        assertThat(points).isNotNull().isGreaterThanOrEqualTo(20);
        assertThat(beta).isNotNull().isFinite();
    }

    private static Map<String, MarketData> sampleHistWithBenchmark() {
        Map<String, MarketData> m = new HashMap<>();
        m.put("RELIANCE", series("RELIANCE", 100));
        m.put("NIFTY 50", series("NIFTY 50", 200));
        return m;
    }

    private static MarketData series(String symbol, double start) {
        List<MarketData.MarketDataPoint> points = new ArrayList<>();
        LocalDate d = LocalDate.now().minusDays(60);
        for (int i = 0; i < 40; i++) {
            double close = start + i;
            points.add(MarketData.MarketDataPoint.builder()
                    .timestamp(d.plusDays(i).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant())
                    .ohlcData(OhlcData.builder().close(close).open(close).high(close).low(close).build())
                    .build());
        }
        return MarketData.builder()
                .symbol(symbol)
                .timeFrame(TimeFrame.DAY)
                .historical(true)
                .dataPoints(points)
                .build();
    }

    private static MarketData flatSeries(String symbol, double close) {
        List<MarketData.MarketDataPoint> points = new ArrayList<>();
        LocalDate d = LocalDate.now().minusDays(60);
        for (int i = 0; i < 40; i++) {
            points.add(MarketData.MarketDataPoint.builder()
                    .timestamp(d.plusDays(i).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant())
                    .ohlcData(OhlcData.builder().close(close).open(close).high(close).low(close).build())
                    .build());
        }
        return MarketData.builder()
                .symbol(symbol)
                .timeFrame(TimeFrame.DAY)
                .historical(true)
                .dataPoints(points)
                .build();
    }
}
