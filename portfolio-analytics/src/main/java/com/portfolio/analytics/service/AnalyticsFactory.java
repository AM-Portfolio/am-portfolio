package com.portfolio.analytics.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.providers.index.IndexAnalyticsProvider;
import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsProvider;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

/**
 * Factory for creating and managing analytics providers for both index and portfolio analytics
 */
@Service
public class AnalyticsFactory {
    
    // Use type-safe maps with explicit generic type parameters
    private final Map<AnalyticsType, IndexAnalyticsProvider<?>> providers = new HashMap<>();
    private final Map<AnalyticsType, PortfolioAnalyticsProvider<?>> portfolioProviders = new HashMap<>();
    
    /**
     * Constructor that automatically registers all analytics providers
     * @param analyticsProviders List of all index analytics providers in the application context
     * @param portfolioAnalyticsProviders List of all portfolio analytics providers in the application context
     */
    public AnalyticsFactory(List<IndexAnalyticsProvider<?>> analyticsProviders, 
                          List<PortfolioAnalyticsProvider<?>> portfolioAnalyticsProviders) {
        analyticsProviders.forEach(provider -> providers.put(provider.getType(), provider));
        portfolioAnalyticsProviders.forEach(provider -> portfolioProviders.put(provider.getType(), provider));
    }
    
    /**
     * Get an index analytics provider by type
     * @param type The analytics type
     * @return The provider for the specified type
     * @param <T> The type of analytics data returned by the provider
     */
    @SuppressWarnings("unchecked") // Safe cast as we control the provider registration
    public <T> IndexAnalyticsProvider<T> getIndexProvider(AnalyticsType type) {
        return (IndexAnalyticsProvider<T>) Optional.ofNullable(providers.get(type))
                .orElseThrow(() -> new IllegalArgumentException("No provider found for analytics type: " + type));
    }
    
    /**
     * Get a portfolio analytics provider by type
     * @param type The analytics type
     * @return The provider for the specified type
     * @param <T> The type of analytics data returned by the provider
     */
    @SuppressWarnings("unchecked") // Safe cast as we control the provider registration
    public <T> PortfolioAnalyticsProvider<T> getPortfolioProvider(AnalyticsType type) {
        return (PortfolioAnalyticsProvider<T>) Optional.ofNullable(portfolioProviders.get(type))
                .orElseThrow(() -> new IllegalArgumentException("No portfolio provider found for analytics type: " + type));
    }
    
    /**
     * Generate portfolio analytics data using the appropriate provider with additional parameters
     * @param type The analytics type
     * @param portfolioId The portfolio ID to generate analytics for
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     * @param <T> The type of analytics data returned
     */
    public <T> T generatePortfolioAnalytics(AnalyticsType type, AdvancedAnalyticsRequest request) {
        PortfolioAnalyticsProvider<T> provider = getPortfolioProvider(type);
        return provider.generateAnalytics(request);
    }

    /**
     * Generate index analytics data using the appropriate provider with additional parameters
     * @param type The analytics type
     * @param indexSymbol The index symbol to generate analytics for
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     * @param <T> The type of analytics data returned
     */
    public <T> T generateIndexAnalytics(AnalyticsType type, AdvancedAnalyticsRequest request) {
        IndexAnalyticsProvider<T> provider = getIndexProvider(type);
        return provider.generateAnalytics(request);
    }
}
