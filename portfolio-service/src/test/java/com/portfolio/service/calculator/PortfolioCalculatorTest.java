package com.portfolio.service.calculator;

import com.portfolio.marketdata.model.BatchSearchResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.model.cache.StockPriceCache;
import com.portfolio.redis.service.StockIndicesRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculatorTest {

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private StockIndicesRedisService stockPriceRedisService;

    private PortfolioCalculator portfolioCalculator;

    private EquityHoldings holding;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        portfolioCalculator = new PortfolioCalculator(marketDataService, stockPriceRedisService, Runnable::run);
        holding = new EquityHoldings();
        holding.setSymbol("TCS");
        holding.setQuantity(10.0);
        holding.setAverageBuyingPrice(3000.0);
        holding.setInvestmentCost(30000.0);

        marketData = MarketData.builder()
                .symbol("TCS")
                .lastPrice(3300.0)
                .ohlc(OhlcData.builder().open(3200.0).close(3250.0).build())
                .instrumentToken(12345L)
                .build();
    }

    @Test
    void enrichHoldings_Success() {
        when(marketDataService.getMarketData(anyList())).thenReturn(Map.of("TCS", marketData));
        
        BatchSearchResponse.SecurityMatch match = new BatchSearchResponse.SecurityMatch();
        match.setMarketCapValue(100000000000L);
        match.setMarketCapType("Large Cap");
        when(marketDataService.getMarketCapData(anyList())).thenReturn(Map.of("TCS", match));

        List<EquityHoldings> results = portfolioCalculator.enrichHoldings(List.of(holding));

        assertEquals(1, results.size());
        EquityHoldings enriched = results.get(0);
        assertEquals(3300.0, enriched.getCurrentPrice());
        assertEquals(33000.0, enriched.getCurrentValue());
        assertEquals(3000.0, enriched.getGainLoss());
        assertEquals(10.0, enriched.getGainLossPercentage());
        assertEquals("Large Cap", enriched.getMarketCapCategory());
    }

    @Test
    void enrichHoldings_FallbackToRedis() {
        when(marketDataService.getMarketData(anyList())).thenReturn(Collections.emptyMap());
        when(marketDataService.getMarketCapData(anyList())).thenReturn(Collections.emptyMap());
        
        StockPriceCache redisPrice = StockPriceCache.builder()
                .closePrice(3100.0)
                .build();
        when(stockPriceRedisService.getLatestPrices(anyList())).thenReturn(Map.of("TCS", redisPrice));

        List<EquityHoldings> results = portfolioCalculator.enrichHoldings(List.of(holding));

        assertEquals(3100.0, results.get(0).getCurrentPrice());
    }

    @Test
    void enrichHoldings_LocalDevFallback() {
        when(marketDataService.getMarketData(anyList())).thenReturn(Collections.emptyMap());
        when(marketDataService.getMarketCapData(anyList())).thenReturn(Collections.emptyMap());
        when(stockPriceRedisService.getLatestPrices(anyList())).thenReturn(Map.of());

        List<EquityHoldings> results = portfolioCalculator.enrichHoldings(List.of(holding));

        // Fallback is 5% gain: 3000 * 1.05 = 3150
        assertEquals(3150.0, results.get(0).getCurrentPrice());
    }

    @Test
    void calculateSummary_Success() {
        holding.setCurrentValue(33000.0);
        holding.setTodayGainLoss(1000.0);
        holding.setGainLoss(3000.0);
        holding.setMarketCap("Large Cap");
        holding.setSector("IT");

        PortfolioSummaryV1 summary = portfolioCalculator.calculateSummary(List.of(holding), 30000.0);

        assertEquals(30000.0, summary.getInvestmentValue());
        assertEquals(33000.0, summary.getCurrentValue());
        assertEquals(3000.0, summary.getTotalGainLoss());
        assertEquals(10.0, summary.getTotalGainLossPercentage());
        assertEquals(1000.0, summary.getTodayGainLoss());
        assertEquals(3.03, summary.getTodayGainLossPercentage()); // (1000/33000)*100
        assertEquals(1, summary.getTotalAssets());
        assertEquals(1, summary.getGainersCount());
        assertEquals(1, summary.getTodayGainersCount());
        assertNotNull(summary.getMarketCapHoldings());
        assertNotNull(summary.getSectorialHoldings());
    }

    @Test
    void calculateWeights_Success() {
        holding.setCurrentValue(1000.0);
        EquityHoldings h2 = new EquityHoldings();
        h2.setSymbol("INFY");
        h2.setCurrentValue(3000.0);

        List<EquityHoldings> list = List.of(holding, h2);
        portfolioCalculator.calculateWeights(list);

        assertEquals(25.0, holding.getWeightInPortfolio());
        assertEquals(75.0, h2.getWeightInPortfolio());
    }
}
