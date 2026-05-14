package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.builder.PortfolioAnalysisBuilder;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.redis.service.PortfolioAnalysisRedisService;
import com.portfolio.service.StockPerformanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private StockPerformanceService stockPerformanceService;

    @Mock
    private PortfolioAnalysisBuilder portfolioAnalysisBuilder;

    @Mock
    private PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    @InjectMocks
    private PortfolioAnalysisService portfolioAnalysisService;

    private static final String PORTFOLIO_ID = UUID.randomUUID().toString();
    private static final String USER_ID = "test-user";

    @Test
    void analyzePortfolio_CacheHit() {
        PortfolioAnalysis cachedAnalysis = PortfolioAnalysis.builder().build();
        when(portfolioAnalysisRedisService.getLatestAnalysis(eq(PORTFOLIO_ID), eq(USER_ID), any()))
                .thenReturn(Optional.of(cachedAnalysis));

        PortfolioAnalysis result = portfolioAnalysisService.analyzePortfolio(PORTFOLIO_ID, USER_ID, 0, 5, null);

        assertNotNull(result);
        assertSame(cachedAnalysis, result);
        verifyNoInteractions(portfolioService, stockPerformanceService, portfolioAnalysisBuilder);
    }

    @Test
    void analyzePortfolio_CacheMiss_BuildSuccess() {
        when(portfolioAnalysisRedisService.getLatestAnalysis(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(UUID.fromString(PORTFOLIO_ID));
        EquityModel equity = new EquityModel();
        equity.setAssetType(AssetType.EQUITY);
        portfolio.setEquityModels(List.of(equity));

        when(portfolioService.getPortfolioById(any(UUID.class))).thenReturn(portfolio);
        
        List<StockPerformance> performances = List.of(StockPerformance.builder().build());
        when(stockPerformanceService.calculateStockPerformances(anyList(), any())).thenReturn(performances);

        PortfolioAnalysis builtAnalysis = PortfolioAnalysis.builder().build();
        when(portfolioAnalysisBuilder.buildAnalysis(anyString(), anyString(), anyList(), anyInt(), anyInt(), any(), any()))
                .thenReturn(builtAnalysis);

        PortfolioAnalysis result = portfolioAnalysisService.analyzePortfolio(PORTFOLIO_ID, USER_ID, 0, 5, null);

        assertNotNull(result);
        assertSame(builtAnalysis, result);
        verify(portfolioAnalysisRedisService).cachePortfolioAnalysis(eq(builtAnalysis), eq(PORTFOLIO_ID), eq(USER_ID), any());
    }

    @Test
    void analyzePortfolio_PortfolioNotFound() {
        when(portfolioAnalysisRedisService.getLatestAnalysis(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(portfolioService.getPortfolioById(any(UUID.class))).thenReturn(null);

        PortfolioAnalysis result = portfolioAnalysisService.analyzePortfolio(PORTFOLIO_ID, USER_ID, 0, 5, null);

        assertNull(result);
    }
}
