package com.portfolio.service;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.StockPerformanceGroup;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import com.portfolio.redis.service.StockIndicesRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockPerformanceServiceTest {

    @Mock
    private StockIndicesRedisService stockPriceRedisService;

    @Mock
    private MarketDataService marketDataService;

    private StockPerformanceService stockPerformanceService;

    @BeforeEach
    void setUp() {
        stockPerformanceService = new StockPerformanceService(
            stockPriceRedisService,
            marketDataService,
            Runnable::run
        );
    }

    @Test
    void calculateStockPerformances_Success() {
        EquityModel asset = new EquityModel();
        asset.setSymbol("TCS");
        asset.setAvgBuyingPrice(3000.0);
        asset.setQuantity(10.0);

        MarketData data = MarketData.builder()
                .symbol("TCS")
                .lastPrice(3300.0)
                .ohlc(OhlcData.builder().close(3200.0).build())
                .build();

        when(marketDataService.getMarketData(anyList())).thenReturn(Map.of("TCS", data));

        List<StockPerformance> results = stockPerformanceService.calculateStockPerformances(List.of(asset), null);

        assertEquals(1, results.size());
        StockPerformance perf = results.get(0);
        assertEquals("TCS", perf.getSymbol());
        assertEquals(3300.0, perf.getCurrentPrice());
        assertEquals(10.0, perf.getGainLossPercentage()); // (3300-3000)/3000 * 100
        assertEquals(3000.0, perf.getGainLoss()); // (3300-3000) * 10
    }

    @Test
    void calculatePerformanceGroup_StatsVerification() {
        StockPerformance p1 = StockPerformance.builder()
                .symbol("S1")
                .gainLossPercentage(10.0)
                .currentPrice(110.0)
                .quantity(1.0)
                .build();
        StockPerformance p2 = StockPerformance.builder()
                .symbol("S2")
                .gainLossPercentage(20.0)
                .currentPrice(120.0)
                .quantity(1.0)
                .build();
        StockPerformance p3 = StockPerformance.builder()
                .symbol("S3")
                .gainLossPercentage(30.0)
                .currentPrice(130.0)
                .quantity(1.0)
                .build();

        StockPerformanceGroup group = stockPerformanceService.calculatePerformanceGroup(
                List.of(p1, p2, p3), 0, 5, true);

        assertEquals(20.0, group.getAveragePerformance());
        assertEquals(20.0, group.getMedianPerformance());
        assertEquals(30.0, group.getBestPerformance());
        assertEquals(10.0, group.getWorstPerformance());
        assertEquals(3, group.getTotalCount());
        
        // Gainers should be sorted descending
        assertEquals("S3", group.getTopPerformers().get(0).getSymbol());
    }

    @Test
    void calculateCurrentValue_Summation() {
        StockPerformance p1 = StockPerformance.builder().currentPrice(100.0).quantity(2.0).build();
        StockPerformance p2 = StockPerformance.builder().currentPrice(200.0).quantity(3.0).build();

        double total = stockPerformanceService.calculateCurrentValue(List.of(p1, p2));

        assertEquals(800.0, total); // (100*2) + (200*3)
    }
}
