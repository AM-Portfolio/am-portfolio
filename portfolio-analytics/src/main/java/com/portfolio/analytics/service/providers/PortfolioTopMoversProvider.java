package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for portfolio top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class PortfolioTopMoversProvider extends AbstractPortfolioAnalyticsProvider<GainerLoser> {

    public static final int DEFAULT_LIMIT = 5;

    public PortfolioTopMoversProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(AdvancedAnalyticsRequest request) {
        // Default to the default limit from TopMoverUtils
        return generateAnalytics(request.getCoreIdentifiers().getPortfolioId(), request);
    }
    
    @Override
    public GainerLoser generateAnalytics(String portfolioId, AdvancedAnalyticsRequest request) {
        // Extract movers limit from feature configuration
        Integer moversLimit = request.getFeatureConfiguration().getMoversLimit();
        int limit = moversLimit != null ? moversLimit : 
                    (request.getPagination().isReturnAllData() ? DEFAULT_LIMIT : request.getPagination().getSize());
        
        log.info("Generating top {} movers for portfolio {} with time frame, pagination, and feature configuration", 
                limit, portfolioId);
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (!isValidPortfolio(portfolio)) {
            return createEmptyResponse();
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return createEmptyResponse();
        }
        
        // Fetch historical market data for all stocks in the portfolio using time frame
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, portfolioSymbols, request.getTimeFrameRequest());
        if (marketData.isEmpty()) {
            log.warn("No historical market data available for portfolio: {}", portfolioId);
            return createEmptyResponse();
        }
        
        // Calculate top movers using the determined limit
        return TopMoverUtils.buildTopMoversResponse(marketData, limit, portfolioId, true);
    }

    
    /**
     * Checks if the portfolio is valid and has holdings
     */
    private boolean isValidPortfolio(PortfolioModelV1 portfolio) {
        return portfolio != null && portfolio.getEquityModels() != null && !portfolio.getEquityModels().isEmpty();
    }
    
    /**
     * Creates an empty response when no data is available
     */
    private GainerLoser createEmptyResponse() {
        return GainerLoser.builder()
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
}
