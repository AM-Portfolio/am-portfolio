package com.portfolio.marketdata.service;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.concurrent.Executor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Mock
    private com.portfolio.redis.service.PortfolioMarketDataRedisService portfolioMarketDataRedisService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService;

    @Mock
    private com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;

    private Executor taskExecutor = Runnable::run;
    private Executor externalApiExecutor = Runnable::run;

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService(marketDataApiClient, portfolioMarketDataRedisService, stockPriceMongoService, stockPriceHistoryMongoService, taskExecutor, externalApiExecutor);
    }

    @Test
    void getOhlcData_Success() {
        List<String> symbols = List.of("TCS");
        MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
        MarketDataResponse response = new MarketDataResponse();
        response.setLastPrice(3300.0);
        wrapper.setData(Map.of("NSE:TCS", response));

        when(marketDataApiClient.getOhlcData(eq(symbols), anyString(), anyBoolean()))
                .thenReturn(Mono.just(wrapper));

        Map<String, MarketData> result = marketDataService.getOhlcData(symbols, false);

        assertNotNull(result);
        assertTrue(result.containsKey("TCS"));
        assertEquals(3300.0, result.get("TCS").getLastPrice());
    }

    @Test
    void getCurrentPrices_Success() {
        List<String> symbols = List.of("INFY");
        MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
        MarketDataResponse resp = new MarketDataResponse();
        resp.setLastPrice(1500.0);
        wrapper.setData(Map.of("INFY", resp));

        when(marketDataApiClient.getOhlcData(anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.just(wrapper));

        Map<String, Double> prices = marketDataService.getCurrentPrices(symbols);

        assertEquals(1500.0, prices.get("INFY"));
    }

    @Test
    void getMarketData_EmptyList_ReturnsEmptyMap() {
        Map<String, MarketData> result = marketDataService.getMarketData(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void getOhlcData_WithEmptySymbols_FiltersThemOut() {
        List<String> symbols = List.of("TCS", "", " ");
        MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
        MarketDataResponse response = new MarketDataResponse();
        response.setLastPrice(3300.0);
        wrapper.setData(Map.of("NSE:TCS", response));

        when(marketDataApiClient.getOhlcData(eq(List.of("TCS")), anyString(), anyBoolean()))
                .thenReturn(Mono.just(wrapper));

        Map<String, MarketData> result = marketDataService.getOhlcData(symbols, false);

        assertNotNull(result);
        assertTrue(result.containsKey("TCS"));
        assertEquals(3300.0, result.get("TCS").getLastPrice());
    }
}
