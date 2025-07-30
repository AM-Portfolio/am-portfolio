package com.portfolio.analytics.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    protected PortfolioModelV1 getPortfolio(String portfolioId) {
        log.info("Fetching portfolio data for ID: {}", portfolioId);
        try {
            UUID portfolioUuid = UUID.fromString(portfolioId);
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(portfolioUuid);
            if (portfolio != null) {
                log.info("Found portfolio: {} with {} holdings", portfolio.getName(), 
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

}
