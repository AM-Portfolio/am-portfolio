package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.AnalyticsFactory;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.CoreIdentifiers;
import com.portfolio.model.analytics.request.FeatureToggles;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.redis.service.PortfolioHeatmapRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsFacadeProgressiveTest {

    @Mock AnalyticsFactory analyticsFactory;
    @Mock PortfolioService portfolioService;
    @Mock MarketDataService marketDataService;
    @Mock SecurityDetailsService securityDetailsService;
    @Mock AdvancedPeriodWarmService warmService;
    @Mock PortfolioHeatmapRedisService heatmapRedisService;

    private PortfolioAnalyticsFacade facade;

    @BeforeEach
    void setUp() {
        Executor sync = Runnable::run;
        facade = new PortfolioAnalyticsFacade(
                analyticsFactory,
                sync,
                portfolioService,
                marketDataService,
                securityDetailsService,
                warmService,
                heatmapRedisService);
    }

    @Test
    void calculateAdvanced_usesOhlcSkipHistRepair_neverCallsHistorical_andEnqueuesWarm() {
        UUID id = UUID.randomUUID();
        EquityModel eq = new EquityModel();
        eq.setSymbol("ADANIENSOL");
        eq.setQuantity(1.0);
        eq.setAvgBuyingPrice(100.0);
        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(id);
        portfolio.setEquityModels(List.of(eq));

        AdvancedAnalyticsRequest request = new AdvancedAnalyticsRequest();
        request.setCoreIdentifiers(CoreIdentifiers.builder().portfolioId(id.toString()).build());
        request.setTimeFrame(TimeFrame.MONTH);
        FeatureToggles toggles = new FeatureToggles();
        toggles.setIncludeHeatmap(true);
        toggles.setIncludeMovers(true);
        toggles.setIncludeSectorAllocation(false);
        toggles.setIncludeMarketCapAllocation(false);
        request.setFeatureToggles(toggles);
        request.setPrefetchedPortfolio(portfolio);

        MarketData md = MarketData.builder().symbol("ADANIENSOL").lastPrice(110.0).previousClose(100.0).build();
        when(marketDataService.getMarketData(anyList(), eq(true))).thenReturn(Map.of("ADANIENSOL", md));
        when(heatmapRedisService.getCachedHeatmap(any(), any())).thenReturn(Optional.empty());
        when(analyticsFactory.generatePortfolioAnalytics(eq(AnalyticsType.SECTOR_HEATMAP), any()))
                .thenReturn(Heatmap.builder().portfolioId(id.toString()).sectors(List.of()).build());
        when(analyticsFactory.generatePortfolioAnalytics(eq(AnalyticsType.TOP_MOVERS), any()))
                .thenReturn(null);

        long start = System.currentTimeMillis();
        AdvancedAnalyticsResponse response = facade.calculateAdvancedAnalytics(request);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 3000, "progressive path should stay well under 3s in unit test, was " + elapsed);
        assertEquals(PortfolioAnalyticsFacade.FRESHNESS_WARMING, response.getAnalyticsFreshness());
        verify(marketDataService, atLeastOnce()).getMarketData(anyList(), eq(true));
        verify(marketDataService, never()).getHistoricalData(ArgumentMatchers.any());
        verify(warmService, atLeastOnce()).warmAsync(any());
    }
}
