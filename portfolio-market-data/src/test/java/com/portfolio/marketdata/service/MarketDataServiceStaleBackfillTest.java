package com.portfolio.marketdata.service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceStaleBackfillTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Mock
    private com.portfolio.redis.service.PortfolioMarketDataRedisService portfolioMarketDataRedisService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;

    private final Executor taskExecutor = Runnable::run;
    private final Executor externalApiExecutor = command -> new Thread(command).start();

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        MarketDataApiConfig config = new MarketDataApiConfig();
        config.setReadTimeout(50);
        MarketDataApiConfig.BatchConfig batch = new MarketDataApiConfig.BatchConfig();
        batch.setMaxParallelChunks(1);
        batch.setParallelBatchingEnabled(false);
        config.setBatch(batch);
        marketDataService = new FailOpenMarketDataService(
                marketDataApiClient,
                portfolioMarketDataRedisService,
                stockPriceMongoService,
                stockPriceHistoryMongoService,
                taskExecutor,
                externalApiExecutor,
                config);
    }

    @Test
    void getOhlcData_onWaveTimeout_backfillsFromStaleCaches() {
        MarketData stale = MarketData.builder()
                .symbol("SLOWSYM")
                .lastPrice(42.5)
                .previousClose(41.0)
                .build();
        when(portfolioMarketDataRedisService.getMarketData(eq(List.of("SLOWSYM"))))
                .thenReturn(Map.of("SLOWSYM", stale));

        when(marketDataApiClient.getOhlcData(eq(List.of("SLOWSYM")), anyString(), anyBoolean()))
                .thenReturn(Mono.delay(Duration.ofMillis(500)).then(Mono.just(new MarketDataResponseWrapper())));

        Map<String, MarketData> result = marketDataService.getOhlcData(List.of("SLOWSYM"), false);

        assertTrue(result.containsKey("SLOWSYM"));
        assertEquals(42.5, result.get("SLOWSYM").getLastPrice());
    }
}
