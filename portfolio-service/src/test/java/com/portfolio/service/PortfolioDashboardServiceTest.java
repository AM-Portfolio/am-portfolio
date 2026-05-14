package com.portfolio.service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.portfolio.PortfolioAnalysisService;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.portfolio.service.portfolio.PortfolioOverviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioDashboardServiceTest {

    @Mock
    private PortfolioAnalysisService portfolioAnalysisService;

    @Mock
    private PortfolioHoldingsService portfolioHoldingsService;

    @Mock
    private PortfolioOverviewService portfolioOverviewService;

    @InjectMocks
    private PortfolioDashboardService portfolioDashboardService;

    @Test
    void overviewPortfolio_DelegatesCorrectly() {
        String userId = "u1";
        TimeInterval interval = TimeInterval.ONE_DAY;
        PortfolioSummaryV1 expected = PortfolioSummaryV1.builder().build();

        when(portfolioOverviewService.overviewPortfolio(userId, interval)).thenReturn(expected);

        PortfolioSummaryV1 result = portfolioDashboardService.overviewPortfolio(userId, interval);

        assertEquals(expected, result);
        verify(portfolioOverviewService).overviewPortfolio(userId, interval);
    }

    @Test
    void analyzePortfolio_DelegatesCorrectly() {
        String pId = "p1";
        String uId = "u1";
        PortfolioAnalysis expected = PortfolioAnalysis.builder().build();

        when(portfolioAnalysisService.analyzePortfolio(pId, uId, 0, 10, TimeInterval.ONE_DAY))
                .thenReturn(expected);

        PortfolioAnalysis result = portfolioDashboardService.analyzePortfolio(pId, uId, 0, 10, TimeInterval.ONE_DAY);

        assertEquals(expected, result);
    }

    @Test
    void getPortfolioHoldings_DelegatesCorrectly() {
        String userId = "u1";
        PortfolioHoldings expected = new PortfolioHoldings();

        when(portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY)).thenReturn(expected);

        PortfolioHoldings result = portfolioDashboardService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY);

        assertEquals(expected, result);
    }
}
