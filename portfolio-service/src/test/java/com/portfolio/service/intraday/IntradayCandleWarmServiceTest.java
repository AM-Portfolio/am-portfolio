package com.portfolio.service.intraday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;
import com.am.common.amcommondata.service.price.StockPriceHistoryMongoService;
import com.am.common.investment.model.historical.OHLCVTPoint;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalChartsResponse;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.intraday.IntradayCandleWarmJobStatus;
import com.portfolio.model.intraday.IntradayCandleWarmJobStatus.Phase;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class IntradayCandleWarmServiceTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;
    @Mock
    private NseIndicesService nseIndicesService;
    @Mock
    private StockPriceHistoryMongoService stockPriceHistoryMongoService;

    /** Run async work inline so tests stay deterministic. */
    private final Executor syncExecutor = Runnable::run;

    private IntradayCandleWarmService service;

    @BeforeEach
    void setUp() {
        service = new IntradayCandleWarmService(
                marketDataApiClient,
                nseIndicesService,
                stockPriceHistoryMongoService,
                syncExecutor);
        ReflectionTestUtils.setField(service, "chunkSize", 2);
    }

    @Test
    void toHistoryDocsMapsCloseAndMinuteEpoch() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 28, 9, 15);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 28, 9, 20);
        List<OHLCVTPoint> points = List.of(
                OHLCVTPoint.builder().time(t1).open(100.0).high(101.0).low(99.0).close(100.5).volume(1L).build(),
                OHLCVTPoint.builder().time(t2).open(100.5).high(102.0).low(100.0).close(101.0).volume(1L).build(),
                OHLCVTPoint.builder().time(t2).open(0.0).high(0.0).low(0.0).close(0.0).volume(0L).build());

        List<StockPriceHistoryDocument> docs = IntradayCandleWarmService.toHistoryDocs("RELIANCE", points);

        assertEquals(2, docs.size());
        assertEquals("RELIANCE", docs.get(0).getSymbol());
        assertEquals(100.5, docs.get(0).getPrice());
        assertEquals(IntradayCandleWarmService.toMinuteEpoch(t1), docs.get(0).getTimestampMinute());
        assertEquals(101.0, docs.get(1).getPrice());
        assertTrue(docs.get(0).getId().startsWith("RELIANCE_"));
    }

    @Test
    void toHistoryDocsEmptyOnBadInput() {
        assertTrue(IntradayCandleWarmService.toHistoryDocs(null, List.of()).isEmpty());
        assertTrue(IntradayCandleWarmService.toHistoryDocs("X", null).isEmpty());
        assertTrue(IntradayCandleWarmService.toHistoryDocs("X", List.of()).isEmpty());
    }

    @Test
    void startWarmWithSymbolsOverrideDoesNotCallIndex() {
        when(marketDataApiClient.getHistoricalCharts(anyList(), eq("1D")))
                .thenReturn(Mono.just(emptyCharts()));

        IntradayCandleWarmJobStatus status = service.startWarm("NIFTY_500", "RELIANCE,TCS");

        assertNotNull(status.getJobId());
        assertEquals("CUSTOM", status.getUniverse());
        assertEquals(2, status.getSymbolTotal());
        verify(nseIndicesService, never()).getConstituentSymbols(any());
        assertEquals(Phase.DONE, service.getStatus(status.getJobId()).getPhase());
        verify(stockPriceHistoryMongoService, never()).saveSessionTicks(anyList());
    }

    @Test
    void chunkFailureDoesNotAbortWholeJob() {
        when(nseIndicesService.getConstituentSymbols("NIFTY 500"))
                .thenReturn(List.of("AAA", "BBB", "CCC"));

        HistoricalChartsResponse ok = new HistoricalChartsResponse();
        Map<String, HistoricalData> data = new HashMap<>();
        data.put("CCC", HistoricalData.builder()
                .tradingSymbol("CCC")
                .dataPoints(List.of(OHLCVTPoint.builder()
                        .time(LocalDateTime.of(2026, 10, 28, 10, 0))
                        .open(1.0).high(1.0).low(1.0).close(10.0).volume(1L)
                        .build()))
                .build());
        ok.setData(data);

        when(marketDataApiClient.getHistoricalCharts(eq(List.of("AAA", "BBB")), eq("1D")))
                .thenReturn(Mono.error(new RuntimeException("timeout")));
        when(marketDataApiClient.getHistoricalCharts(eq(List.of("CCC")), eq("1D")))
                .thenReturn(Mono.just(ok));
        when(stockPriceHistoryMongoService.saveSessionTicks(anyList())).thenAnswer(inv -> {
            List<?> docs = inv.getArgument(0);
            return docs.size();
        });

        IntradayCandleWarmJobStatus started = service.startWarm("NIFTY_500", null);
        IntradayCandleWarmJobStatus done = service.getStatus(started.getJobId());

        assertEquals(Phase.DONE, done.getPhase());
        assertEquals(2, done.getSymbolsFailed());
        assertEquals(1, done.getSymbolsSucceeded());
        assertTrue(done.getTicksWritten() >= 1);
        verify(stockPriceHistoryMongoService, times(1)).saveSessionTicks(anyList());
    }

    @Test
    void startWarmRejectsEmptySymbolList() {
        when(nseIndicesService.getConstituentSymbols("NIFTY 500")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.startWarm("NIFTY_500", null));
    }

    @Test
    void startWarmRequiresMongo() {
        IntradayCandleWarmService noMongo = new IntradayCandleWarmService(
                marketDataApiClient, nseIndicesService, null, syncExecutor);
        assertThrows(IllegalStateException.class, () -> noMongo.startWarm("NIFTY_500", "RELIANCE"));
    }

    private static HistoricalChartsResponse emptyCharts() {
        HistoricalChartsResponse r = new HistoricalChartsResponse();
        r.setData(Map.of());
        return r;
    }
}
