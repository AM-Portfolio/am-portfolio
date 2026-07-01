package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractAnalyticsProvider;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;

/**
 * Abstract base class for portfolio analytics providers
 * @param <T> The type of analytics data returned
 */
@Slf4j
public abstract class AbstractPortfolioAnalyticsProvider<T> extends AbstractAnalyticsProvider<T, String> implements PortfolioAnalyticsProvider<T> {
    
    protected final PortfolioService portfolioService;
    
    /**
     * Constructor for AbstractPortfolioAnalyticsProvider
     * 
     * @param portfolioService Service for fetching portfolio data
     * @param marketDataService Service for fetching market data
     * @param securityDetailsService Service for security metadata
     */
    public AbstractPortfolioAnalyticsProvider(PortfolioService portfolioService, 
                                            MarketDataService marketDataService, 
                                            SecurityDetailsService securityDetailsService) {
        super(marketDataService, securityDetailsService);
        this.portfolioService = portfolioService;
    }
    
    /**
     * Get portfolio data for a given portfolio ID
     * @param portfolioId The portfolio ID to fetch data for
     * @return Portfolio model or null if not found
     */
    @Cacheable(value = "portfolioCache", key = "#portfolioId")
    protected PortfolioModelV1 getPortfolio(String portfolioId) {
        log.info("Fetching portfolio data for ID: {}", portfolioId);
        try {
            UUID portfolioUuid = UUID.fromString(portfolioId);
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(portfolioUuid);
            if (portfolio != null) {
                log.info("Found portfolio: {} with {} holdings", portfolioId, 
                        portfolio.getEquityModels() != null ? portfolio.getEquityModels().size() : 0);
                return portfolio;
            } else {
                log.warn("No portfolio found with ID: {}", portfolioId);
                return null;
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid portfolio ID format: {}", portfolioId, e);
            return null;
        } catch (Exception e) {
            log.error("Error fetching portfolio: {}", portfolioId, e);
            return null;
        }
    }
    
    /**
     * Extract stock symbols from portfolio holdings
     * @param portfolio The portfolio model
     * @return List of stock symbols in the portfolio
     */
    protected List<String> getPortfolioSymbols(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return Collections.emptyList();
        }
        
        return portfolio.getEquityModels().stream()
            .map(EquityModel::getSymbol)
            .filter(symbol -> symbol != null && !symbol.isEmpty())
            .collect(Collectors.toList());
    }
    
    @Override
    protected List<String> getSymbols(String portfolioId) {
        // Get portfolio data and extract symbols
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null) {
            return Collections.emptyList();
        }
        return getPortfolioSymbols(portfolio);
    }
    
    @Override
    public T generateAnalytics(String portfolioId, AdvancedAnalyticsRequest request) {
        log.info("Generating {} analytics for portfolio {} with time frame: {} to {}, interval: {}", 
                getType(), portfolioId, request.getFromDate(), 
                request.getToDate(), request.getTimeFrame());
        return generateAnalytics(portfolioId, request);
    }
    
    /**
     * Process portfolio data with common validation and market data fetching pattern.
     * This method handles the common code pattern found in multiple portfolio providers.
     * 
     * @param <R> The type of result to return
     * @param portfolioId The portfolio ID to process
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @param emptyResultSupplier Supplier for creating an empty result when validation fails
     * @param resultProcessor Function to process the data when validation passes
     * @return The result of type R
     */
    protected <R> R processPortfolioData(
            String portfolioId,
            TimeFrameRequest timeFrameRequest,
            Supplier<R> emptyResultSupplier,
            PortfolioDataProcessor<R> resultProcessor) {
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return emptyResultSupplier.get();
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return emptyResultSupplier.get();
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, portfolioSymbols, timeFrameRequest);
        
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return emptyResultSupplier.get();
        }
        
        // Process the data with the provided processor
        return resultProcessor.process(portfolio, portfolioSymbols, marketData);
    }
    
    /**
     * Functional interface for processing portfolio data
     * @param <R> The type of result to return
     */
    @FunctionalInterface
    protected interface PortfolioDataProcessor<R> {
        R process(PortfolioModelV1 portfolio, List<String> symbols, Map<String, MarketData> marketData);
    }
    
    /**
     * Process portfolio data with a Hybrid Circuit Breaker approach.
     * Attempts to fetch full Market Data for Global Timeframe support (8-second timeout).
     * If it times out or fails, gracefully falls back to MongoDB local extraction.
     */
    protected <R> R processPortfolioDataHybrid(
            String portfolioId,
            TimeFrameRequest timeFrameRequest,
            Supplier<R> emptyResultSupplier,
            PortfolioDataProcessor<R> marketDataProcessor,
            PortfolioDataLocalProcessor<R> fallbackLocalProcessor) {
        
        // Get portfolio data (Automatically cached in Redis)
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return emptyResultSupplier.get();
        }
        
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            return emptyResultSupplier.get();
        }
        
        java.util.concurrent.CompletableFuture<Map<String, MarketData>> future = null;
        try {
            log.info("Circuit Breaker [START]: Attempting to fetch Market Data for portfolio: {} with 8s timeout...", portfolioId);
            
            // Execute Market Data fetch in a separate thread to enforce timeout
            future = java.util.concurrent.CompletableFuture.supplyAsync(() -> 
                    AnalyticsUtils.fetchMarketData(this, portfolioSymbols, timeFrameRequest)
                );
                
            // Strict 8-second Timeout
            Map<String, MarketData> marketData = future.get(8, java.util.concurrent.TimeUnit.SECONDS);
            
            if (marketData != null && !marketData.isEmpty()) {
                log.info("Circuit Breaker [SUCCESS]: Market data fetched in time. Executing Primary Processor (Global Timeframes).");
                return marketDataProcessor.process(portfolio, portfolioSymbols, marketData);
            } else {
                log.warn("Circuit Breaker [WARN]: Market data returned empty. Triggering Fallback.");
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Circuit Breaker [TRIPPED - TIMEOUT]: Market Data API took longer than 8s. Cancelling task and falling back to MongoDB 1D Snapshot for portfolio: {}", portfolioId);
            if (future != null) {
                future.cancel(true); // Prevent thread leak!
            }
        } catch (Exception e) {
            log.warn("Circuit Breaker [TRIPPED - ERROR]: Failed to fetch Market Data ({}). Falling back to MongoDB 1D Snapshot for portfolio: {}", e.getMessage(), portfolioId);
        }
        
        // Fallback: Process local MongoDB data
        log.info("Executing Fallback Local Processor (Instant MongoDB Extraction) for portfolio: {}", portfolioId);
        return fallbackLocalProcessor.process(portfolio);
    }
    
    /**
     * Functional interface for processing portfolio data locally
     * @param <R> The type of result to return
     */
    @FunctionalInterface
    protected interface PortfolioDataLocalProcessor<R> {
        R process(PortfolioModelV1 portfolio);
    }
}
