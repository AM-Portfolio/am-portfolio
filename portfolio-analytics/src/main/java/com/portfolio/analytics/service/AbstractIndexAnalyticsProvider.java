package com.portfolio.analytics.service;

import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.marketdata.model.indices.IndexConstituent;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for index analytics providers
 * @param <T> The type of analytics data returned
 */
@Slf4j
public abstract class AbstractIndexAnalyticsProvider<T> extends AbstractAnalyticsProvider<T, String> implements IndexAnalyticsProvider<T> {
    
    protected final NseIndicesService nseIndicesService;
    
    /**
     * Constructor for AbstractIndexAnalyticsProvider
     * 
     * @param nseIndicesService Service for fetching index constituents
     * @param marketDataService Service for fetching market data
     * @param securityDetailsService Service for security metadata
     */
    public AbstractIndexAnalyticsProvider(NseIndicesService nseIndicesService, 
                                         MarketDataService marketDataService, 
                                         SecurityDetailsService securityDetailsService) {
        super(marketDataService, securityDetailsService);
        this.nseIndicesService = nseIndicesService;
    }

        
    @Override
    public T generateAnalytics(String indexSymbol, AdvancedAnalyticsRequest request) {
        log.info("Generating {} analytics for index {} with time frame, pagination, and feature configuration", 
                getType(), indexSymbol);
        return generateAnalytics(indexSymbol, request);
    }
    
    /**
     * Fetch all stock symbols for a given index
     * @param indexSymbol The index symbol to fetch stock symbols for
     * @return List of stock symbols in the index
     */
    protected List<String> getIndexSymbols(String indexSymbol) {
        log.info("Fetching symbols for index: {}", indexSymbol);
        try {
            var indexConstituents = nseIndicesService.getIndexConstituents(indexSymbol);
            if (indexConstituents != null) {
                log.info("Found {} symbols for index {}", indexConstituents.size(), indexSymbol);
                return indexConstituents.stream()
                    .map(IndexConstituent::getSymbol)
                    .toList();
            } else {
                log.warn("No data found for index: {}", indexSymbol);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching symbols for index: {}", indexSymbol, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    protected List<String> getSymbols(String indexSymbol) {
        // Delegate to the existing method
        return getIndexSymbols(indexSymbol);
    }

}
