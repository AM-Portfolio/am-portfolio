package com.portfolio.marketdata.service;

import com.am.common.investment.model.historical.OHLCVTPoint;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServicePriorCloseRepairTest {

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

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService(
                marketDataApiClient,
                portfolioMarketDataRedisService,
                stockPriceMongoService,
                stockPriceHistoryMongoService,
                taskExecutor,
                externalApiExecutor);
    }

    @Test
    void needsPriorSessionClose_whenLastEqualsPrev() {
        MarketData md = MarketData.builder().lastPrice(100.0).previousClose(100.0).build();
        assertTrue(MarketDataService.needsPriorSessionClose(md));
    }

    @Test
    void needsPriorSessionClose_whenDistinct() {
        MarketData md = MarketData.builder().lastPrice(105.0).previousClose(100.0).build();
        assertFalse(MarketDataService.needsPriorSessionClose(md));
    }

    @Test
    void resolvePriorClose_walksBackPastCollapsedBars() {
        List<MarketData.MarketDataPoint> points = new ArrayList<>();
        points.add(mdPoint(98.0));
        points.add(mdPoint(100.0));
        points.add(mdPoint(100.0));
        MarketData hist = MarketData.builder().dataPoints(points).build();

        Double prior = MarketDataService.resolvePriorCloseFromHistorical(hist, 100.0);
        assertEquals(98.0, prior);
    }

    @Test
    void repairCollapsedPreviousClose_appliesHistPriorAndWritesRedis() {
        MarketData live = MarketData.builder()
                .symbol("INFY")
                .lastPrice(1366.93)
                .previousClose(1366.93)
                .build();
        Map<String, MarketData> result = new java.util.HashMap<>();
        result.put("INFY", live);

        when(marketDataApiClient.getHistoricalData(any())).thenReturn(Mono.just(histWrapper(
                "INFY",
                List.of(1340.0, 1350.0, 1366.93))));

        marketDataService.repairCollapsedPreviousClose(result);

        assertEquals(1350.0, result.get("INFY").getPreviousClose());
        verify(portfolioMarketDataRedisService, atLeastOnce()).cacheMarketData(anyMap());
    }

    @Test
    void repairCollapsedPreviousClose_histMiss_doesNotPoisonPriorCache() {
        MarketData live = MarketData.builder()
                .symbol("TCS")
                .lastPrice(3173.25)
                .previousClose(3173.25)
                .build();
        Map<String, MarketData> result = new java.util.HashMap<>();
        result.put("TCS", live);

        when(marketDataApiClient.getHistoricalData(any())).thenReturn(Mono.empty());

        marketDataService.repairCollapsedPreviousClose(result);

        assertEquals(3173.25, result.get("TCS").getPreviousClose());
        clearInvocations(marketDataApiClient);

        MarketData live2 = MarketData.builder()
                .symbol("TCS")
                .lastPrice(3173.25)
                .previousClose(3173.25)
                .build();
        Map<String, MarketData> again = new java.util.HashMap<>();
        again.put("TCS", live2);
        marketDataService.repairCollapsedPreviousClose(again);

        verify(marketDataApiClient, never()).getHistoricalData(any());
        assertEquals(3173.25, again.get("TCS").getPreviousClose());
    }

    private static MarketData.MarketDataPoint mdPoint(double close) {
        return MarketData.MarketDataPoint.builder()
                .ohlcData(OhlcData.builder().close(close).open(close).high(close).low(close).build())
                .build();
    }

    private static HistoricalDataResponseWrapper histWrapper(String symbol, List<Double> closes) {
        List<OHLCVTPoint> pts = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 15, 30);
        for (Double c : closes) {
            OHLCVTPoint p = new OHLCVTPoint();
            p.setTime(t);
            p.setOpen(c);
            p.setHigh(c);
            p.setLow(c);
            p.setClose(c);
            p.setVolume(1L);
            pts.add(p);
            t = t.plusDays(1);
        }
        HistoricalDataResponse raw = HistoricalDataResponse.builder()
                .symbol(symbol)
                .tradingSymbol(symbol)
                .interval("day")
                .dataPoints(pts)
                .build();
        HistoricalDataResponseWrapper wrapper = new HistoricalDataResponseWrapper();
        wrapper.setData(Map.of(symbol, raw));
        return wrapper;
    }
}
