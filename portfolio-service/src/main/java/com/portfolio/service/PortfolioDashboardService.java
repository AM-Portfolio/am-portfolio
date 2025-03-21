package com.portfolio.service;

import org.springframework.stereotype.Service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.portfolio.PortfolioAnalysisService;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.portfolio.service.portfolio.PortfolioOverviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class for portfolio dashboard operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioDashboardService {
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioOverviewService portfolioOverviewService;

    /**
     * Provides an overview of the portfolio for the given user and time interval.
     * 
     * @param userId   the ID of the user
     * @param interval the time interval
     * @return the portfolio summary
     */
    public PortfolioSummaryV1 overviewPortfolio(String userId, TimeInterval interval) {
        log.info("PortfolioDashboardService - Starting overviewPortfolio for user: {}, interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
        
        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, interval);
        
        log.info("PortfolioDashboardService - Completed overviewPortfolio for user: {}, found summary: {}", 
            userId, result != null ? "yes" : "no");
        return result;
    }

    /**
     * Analyzes the portfolio for the given portfolio ID, user ID, page number, page size, and time interval.
     * 
     * @param portfolioId the ID of the portfolio
     * @param userId      the ID of the user
     * @param pageNumber  the page number
     * @param pageSize    the page size
     * @param interval    the time interval
     * @return the portfolio analysis
     */
    public PortfolioAnalysis analyzePortfolio(String portfolioId, String userId, Integer pageNumber, Integer pageSize, TimeInterval interval) {
        log.info("PortfolioDashboardService - Starting analyzePortfolio - Portfolio: {}, User: {}, Page: {}, Size: {}, Interval: {}", 
            portfolioId, userId, pageNumber, pageSize, interval != null ? interval.getCode() : "null");
        
        PortfolioAnalysis result = portfolioAnalysisService.analyzePortfolio(portfolioId, userId, pageNumber, pageSize, interval);
        
        log.info("PortfolioDashboardService - Completed analyzePortfolio for portfolio: {}, found analysis: {}", 
            portfolioId, result != null ? "yes" : "no");
        return result;
    }

    /**
     * Retrieves the portfolio holdings for the given user and time interval.
     * 
     * @param userId   the ID of the user
     * @param interval the time interval
     * @return the portfolio holdings
     */
    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        log.info("PortfolioDashboardService - Starting getPortfolioHoldings for user: {}, interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
        
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, interval);
        
        log.info("PortfolioDashboardService - Completed getPortfolioHoldings for user: {}, found holdings: {}", 
            userId, result != null ? "yes" : "no");
        return result;
    }
}
