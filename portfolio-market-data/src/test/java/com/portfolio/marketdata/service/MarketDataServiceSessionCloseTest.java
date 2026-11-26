package com.portfolio.marketdata.service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceSessionCloseTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Mock
    private com.portfolio.redis.service.PortfolioMarketDataRedisService portfolioMarketDataRedisService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;

    private final Executor taskExecutor = Runnable::run;
    private final Executor externalApiExecutor = Runnable::run;

    private MarketDataService marketDataService;
    private FailOpenMarketDataService failOpenService;

    @BeforeEach
    void setUp() {
        MarketDataApiConfig config = new MarketDataApiConfig();
        MarketDataApiConfig.BatchConfig batch = new MarketDataApiConfig.BatchConfig();
        batch.setChunkSize(450);
        batch.setParallelBatchingEnabled(true);
        batch.setMaxParallelChunks(4);
        batch.setSessionCloseWaveTimeoutMs(15_000L);
        config.setBatch(batch);

        marketDataService = new MarketDataService(
                marketDataApiClient,
                portfolioMarketDataRedisService,
                stockPriceMongoService,
                stockPriceHistoryMongoService,
                taskExecutor,
                externalApiExecutor,
                config);
        failOpenService = new FailOpenMarketDataService(
                marketDataApiClient,
                portfolioMarketDataRedisService,
                stockPriceMongoService,
                stockPriceHistoryMongoService,
                taskExecutor,
                externalApiExecutor,
                config);
    }

    @Test
    void sessionClose_afterHours_prefersOhlcOverMiddayMongo() {
        when(portfolioMarketDataRedisService.isCashMarketHours()).thenReturn(false);

        MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
        MarketDataResponse response = new MarketDataResponse();
        response.setLastPrice(2517.3);
        response.setPreviousClose(2460.0);
        wrapper.setData(Map.of("TCS", response));
        when(marketDataApiClient.getOhlcData(eq(List.of("TCS")), anyString(), anyBoolean()))
                .thenReturn(Mono.just(wrapper));

        Map<String, MarketData> result =
                marketDataService.getMarketData(List.of("TCS"), QuoteFreshness.SESSION_CLOSE);

        MarketData tcs = result.get("TCS");
        assertNotNull(tcs);
        assertEquals(2517.3, tcs.getLastPrice(), 0.01);
        assertEquals(2460.0, tcs.getPreviousClose(), 0.01);
        double pct = (tcs.getLastPrice() - tcs.getPreviousClose()) / tcs.getPreviousClose() * 100.0;
        assertEquals(2.33, pct, 0.3);
        // OHLC first — Mongo must not be consulted before OHLC succeeds
        verify(stockPriceMongoService, never()).getPrices(anyList());
        verify(marketDataApiClient, times(1)).getOhlcData(anyList(), anyString(), anyBoolean());
    }

    @Test
    void sessionClose_singleBulkCall_forManySymbols() {
        when(portfolioMarketDataRedisService.isCashMarketHours()).thenReturn(false);

        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            symbols.add("SYM" + i);
        }

        AtomicInteger calls = new AtomicInteger();
        when(marketDataApiClient.getOhlcData(anyList(), anyString(), anyBoolean())).thenAnswer(inv -> {
            calls.incrementAndGet();
            @SuppressWarnings("unchecked")
            List<String> chunk = inv.getArgument(0);
            MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
            java.util.HashMap<String, MarketDataResponse> data = new java.util.HashMap<>();
            for (String s : chunk) {
                MarketDataResponse r = new MarketDataResponse();
                r.setLastPrice(100.0);
                r.setPreviousClose(99.0);
                data.put(s, r);
            }
            wrapper.setData(data);
            return Mono.just(wrapper);
        });

        Map<String, MarketData> result =
                marketDataService.getMarketData(symbols, QuoteFreshness.SESSION_CLOSE);

        assertEquals(120, result.size());
        assertEquals(1, calls.get(), "N<=450 must be one OHLC HTTP call");
    }

    @Test
    void failOpen_sessionClose_doesNotBackfillFromRedis() {
        when(portfolioMarketDataRedisService.isCashMarketHours()).thenReturn(false);
        when(stockPriceMongoService.getPrices(anyList())).thenReturn(Map.of());
        when(marketDataApiClient.getOhlcData(anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.just(new MarketDataResponseWrapper()));

        Map<String, MarketData> result =
                failOpenService.getMarketData(List.of("TCS"), QuoteFreshness.SESSION_CLOSE);

        // OHLC empty + FailOpen skipped + Mongo empty → no stale Redis poison
        assertTrue(result.isEmpty() || result.get("TCS") == null
                || result.get("TCS").getLastPrice() == null
                || result.get("TCS").getLastPrice() <= 0);
        verify(portfolioMarketDataRedisService, never()).getMarketData(anyList());
    }

    @Test
    void sessionClose_cashOpen_skipsL1_usesRedis() {
        when(portfolioMarketDataRedisService.isCashMarketHours()).thenReturn(true);
        MarketData cached = MarketData.builder()
                .symbol("INFY")
                .lastPrice(1500.0)
                .previousClose(1490.0)
                .build();
        when(portfolioMarketDataRedisService.getMarketData(eq(List.of("INFY"))))
                .thenReturn(Map.of("INFY", cached));

        // Prime L1 via NORMAL path
        marketDataService.getMarketData(List.of("INFY"), QuoteFreshness.NORMAL);
        // Change Redis to prove SESSION_CLOSE does not serve L1
        MarketData fresher = MarketData.builder()
                .symbol("INFY")
                .lastPrice(1510.0)
                .previousClose(1490.0)
                .build();
        when(portfolioMarketDataRedisService.getMarketData(eq(List.of("INFY"))))
                .thenReturn(Map.of("INFY", fresher));

        Map<String, MarketData> result =
                marketDataService.getMarketData(List.of("INFY"), QuoteFreshness.SESSION_CLOSE);

        assertEquals(1510.0, result.get("INFY").getLastPrice());
        verify(marketDataApiClient, never()).getOhlcData(anyList(), anyString(), anyBoolean());
    }
}
