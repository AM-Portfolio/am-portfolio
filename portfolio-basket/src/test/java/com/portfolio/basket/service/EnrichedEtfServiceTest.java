package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.model.basket.cache.CachedEtfData;
import com.portfolio.model.basket.cache.CachedEtfHolding;
import com.portfolio.redis.service.BasketEtfRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrichedEtfServiceTest {

    @Mock
    private EtfApiClient etfApiClient;

    @Mock
    private BasketEtfRedisService basketEtfRedisService;

    @Mock
    private BasketCatalogService catalogService;

    @Mock
    private EtfPerformanceEnricher performanceEnricher;

    private EnrichedEtfService service;

    @BeforeEach
    void setUp() {
        service = new EnrichedEtfService(etfApiClient, basketEtfRedisService, catalogService, performanceEnricher);
        ReflectionTestUtils.setField(service, "etfL1TtlSeconds", 86400L);
        ReflectionTestUtils.setField(service, "discoverSkipPerfHist", true);
        service.initL1();
        lenient().when(performanceEnricher.fillMissing(any())).thenReturn(0);
        lenient().when(performanceEnricher.fillDiscoverWave(any())).thenReturn(0);
    }

    @Test
    void getEnrichedEtf_ReturnsNull_WhenSymbolIsBlank() {
        assertNull(service.getEnrichedEtf(null));
        assertNull(service.getEnrichedEtf("  "));
    }

    @Test
    void getEnrichedEtf_FetchesFromApi_WhenRedisFailsOpen() {
        EnrichedEtfService noRedis = new EnrichedEtfService(etfApiClient, null, catalogService, performanceEnricher);
        ReflectionTestUtils.setField(noRedis, "etfL1TtlSeconds", 86400L);
        noRedis.initL1();

        EtfData live = sampleEtf("NIFTYBEES");
        when(etfApiClient.fetchEtfHoldings("NIFTYBEES")).thenReturn(live);

        EtfData first = noRedis.getEnrichedEtf("NIFTYBEES");
        EtfData second = noRedis.getEnrichedEtf("NIFTYBEES");

        assertNotNull(first);
        assertEquals("NIFTYBEES", first.getSymbol());
        assertEquals(first.getName(), second.getName());
        verify(etfApiClient, times(1)).fetchEtfHoldings("NIFTYBEES");
        verify(etfApiClient, times(1)).enrichHoldings(any());
    }

    @Test
    void redisThrows_failOpenToLive() {
        when(basketEtfRedisService.getEnrichedEtf(anyString())).thenThrow(new RuntimeException("redis down"));
        EtfData live = sampleEtf("BANKBEES");
        when(etfApiClient.fetchEtfHoldings("BANKBEES")).thenReturn(live);

        EtfData result = service.getEnrichedEtf("BANKBEES");

        assertNotNull(result);
        assertEquals("BANKBEES", result.getSymbol());
        verify(etfApiClient).enrichHoldings(any());
    }

    @Test
    void redisHit_skipsLiveAndEnrichment() {
        CachedEtfData cached = new CachedEtfData();
        cached.setSymbol("ITBEES");
        cached.setName("IT Bees");
        CachedEtfHolding holding = new CachedEtfHolding();
        holding.setSymbol("TCS");
        holding.setIsin("INE467B01029");
        holding.setWeight(10.0);
        cached.setHoldings(List.of(holding));

        when(basketEtfRedisService.getEnrichedEtf("ITBEES")).thenReturn(Optional.of(cached));

        EtfData result = service.getEnrichedEtf("ITBEES");

        assertEquals("ITBEES", result.getSymbol());
        assertEquals(1, result.getHoldings().size());
        verify(etfApiClient, never()).fetchEtfHoldings(anyString());
        verify(etfApiClient, never()).enrichHoldings(any());
    }

    @Test
    void batchDedupsEnrichmentAcrossEtfs() {
        when(basketEtfRedisService.getEnrichedEtf(anyString())).thenReturn(Optional.empty());

        EtfData a = sampleEtf("NIFTYBEES");
        EtfData b = sampleEtf("BANKBEES");
        when(etfApiClient.fetchEtfHoldingsBatch(any())).thenReturn(Map.of(
                "NIFTYBEES", a,
                "BANKBEES", b));

        Map<String, EtfData> result = service.getEnrichedEtfsBatch(List.of("NIFTYBEES", "BANKBEES"));

        assertEquals(2, result.size());
        verify(etfApiClient, times(1)).enrichHoldings(any());
        verify(performanceEnricher, times(1)).fillMissing(any());
    }

    @Test
    void discoverBatch_skipsEnrichAndPerfHist_whenIsinsPresent() {
        when(basketEtfRedisService.getEnrichedEtf(anyString())).thenReturn(Optional.empty());

        EtfData a = sampleEtfWithIsin("NIFTYBEES");
        EtfData b = sampleEtfWithIsin("BANKBEES");
        when(etfApiClient.fetchEtfHoldingsBatch(any())).thenReturn(Map.of(
                "NIFTYBEES", a,
                "BANKBEES", b));

        Map<String, EtfData> result = service.getEnrichedEtfsBatch(List.of("NIFTYBEES", "BANKBEES"), true);

        assertEquals(2, result.size());
        verify(etfApiClient, times(1)).fetchEtfHoldingsBatch(any());
        verify(etfApiClient, never()).enrichHoldings(any());
        verify(performanceEnricher, never()).fillMissing(any());
        verify(performanceEnricher, times(1)).fillDiscoverWave(any());
    }

    @Test
    void discoverBatch_enrichesWhenIsinsMissing() {
        when(basketEtfRedisService.getEnrichedEtf(anyString())).thenReturn(Optional.empty());

        EtfData a = sampleEtf("NIFTYBEES"); // no ISIN → match would be 0 without enrich
        when(etfApiClient.fetchEtfHoldingsBatch(any())).thenReturn(Map.of("NIFTYBEES", a));

        Map<String, EtfData> result = service.getEnrichedEtfsBatch(List.of("NIFTYBEES"), true);

        assertEquals(1, result.size());
        verify(etfApiClient, times(1)).enrichHoldings(any());
        verify(performanceEnricher, never()).fillMissing(any());
        verify(performanceEnricher, times(1)).fillDiscoverWave(any());
    }

    @Test
    void discoverBatch_l1HitNeedingPerf_fillsDiscoverWaveNotWeeklyHist() {
        EtfData cached = sampleEtfWithIsin("ITBEES");
        cached.setReturn1Y(null);
        ReflectionTestUtils.invokeMethod(service, "store", "ITBEES", cached);

        Map<String, EtfData> result = service.getEnrichedEtfsBatch(List.of("ITBEES"), true);

        assertEquals(1, result.size());
        verify(etfApiClient, never()).fetchEtfHoldingsBatch(any());
        verify(performanceEnricher, never()).fillMissing(any());
        verify(performanceEnricher, times(1)).fillDiscoverWave(any());
    }

    @Test
    void discoverBatch_completeL1_skipsHist() {
        EtfData cached = sampleEtfWithIsin("ITBEES");
        cached.setReturn1Y(1.0);
        cached.setReturn3Y(2.0);
        cached.setReturn5Y(3.0);
        cached.setSparklineCloses(List.of(100.0, 110.0));
        ReflectionTestUtils.invokeMethod(service, "store", "ITBEES", cached);

        Map<String, EtfData> result = service.getEnrichedEtfsBatch(List.of("ITBEES"), true);

        assertEquals(1, result.size());
        assertEquals(1.0, result.get("ITBEES").getReturn1Y());
        verify(etfApiClient, never()).fetchEtfHoldingsBatch(any());
        verify(performanceEnricher, never()).fillMissing(any());
        verify(performanceEnricher, never()).fillDiscoverWave(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverBatch_twoThreads_shareOneWave() throws Exception {
        EtfData cached = sampleEtfWithIsin("ITBEES");
        cached.setReturn1Y(null);
        ReflectionTestUtils.invokeMethod(service, "store", "ITBEES", cached);
        when(performanceEnricher.fillDiscoverWave(any())).thenAnswer(inv -> {
            Thread.sleep(150);
            EtfData etf = ((List<EtfData>) inv.getArgument(0)).get(0);
            etf.setReturn1Y(9.0);
            etf.setReturn3Y(8.0);
            etf.setReturn5Y(7.0);
            etf.setSparklineCloses(List.of(100.0, 110.0));
            return 1;
        });

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> service.getEnrichedEtfsBatch(List.of("ITBEES"), true));
            var b = pool.submit(() -> service.getEnrichedEtfsBatch(List.of("ITBEES"), true));
            assertEquals(1, a.get().size());
            assertEquals(1, b.get().size());
        } finally {
            pool.shutdownNow();
        }
        verify(performanceEnricher, times(1)).fillDiscoverWave(any());
    }

    @Test
    void copyEtf_preservesDiscoverPerformanceFields() {
        EtfData src = sampleEtf("NIFTYBEES");
        src.setCategoryLabel("Large cap · Equity");
        src.setReturn1Y(-1.01);
        src.setReturn3Y(27.5);
        src.setReturn5Y(43.9);
        src.setReturnsAsOf("2026-08-31");
        src.setSparklineCloses(List.of(100.0, 110.0, 105.0));

        EtfData copy = EnrichedEtfService.copyEtf(src);

        assertEquals("Large cap · Equity", copy.getCategoryLabel());
        assertEquals(-1.01, copy.getReturn1Y());
        assertEquals(27.5, copy.getReturn3Y());
        assertEquals(43.9, copy.getReturn5Y());
        assertEquals("2026-08-31", copy.getReturnsAsOf());
        assertEquals(List.of(100.0, 110.0, 105.0), copy.getSparklineCloses());
        // defensive copy
        copy.getSparklineCloses().set(0, 1.0);
        assertEquals(100.0, src.getSparklineCloses().get(0));
    }

    private static EtfData sampleEtf(String symbol) {
        EtfData data = new EtfData();
        data.setSymbol(symbol);
        data.setName(symbol + " Name");
        EtfHolding h = new EtfHolding();
        h.setSymbol("RELIANCE");
        h.setWeight(5.0);
        data.setHoldings(List.of(h));
        return data;
    }

    private static EtfData sampleEtfWithIsin(String symbol) {
        EtfData data = sampleEtf(symbol);
        data.getHoldings().get(0).setIsin("INE002A01018");
        return data;
    }
}
