package com.portfolio.analytics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating and managing analytics providers
 */
@Service
public class AnalyticsFactory {
    
    private final Map<AnalyticsType, AnalyticsProvider<?>> providers = new HashMap<>();
    
    /**
     * Constructor that automatically registers all analytics providers
     * @param analyticsProviders List of all analytics providers in the application context
     */
    @Autowired
    public AnalyticsFactory(List<AnalyticsProvider<?>> analyticsProviders) {
        analyticsProviders.forEach(provider -> providers.put(provider.getType(), provider));
    }
    
    /**
     * Get an analytics provider by type
     * @param type The analytics type
     * @return The provider for the specified type
     * @param <T> The type of analytics data returned by the provider
     */
    @SuppressWarnings("unchecked")
    public <T> AnalyticsProvider<T> getProvider(AnalyticsType type) {
        return (AnalyticsProvider<T>) Optional.ofNullable(providers.get(type))
                .orElseThrow(() -> new IllegalArgumentException("No provider found for analytics type: " + type));
    }
    
    /**
     * Generate analytics data using the appropriate provider
     * @param type The analytics type
     * @param symbol The symbol to generate analytics for
     * @return The analytics data
     * @param <T> The type of analytics data returned
     */
    @SuppressWarnings("unchecked")
    public <T> T generateAnalytics(AnalyticsType type, String symbol) {
        AnalyticsProvider<T> provider = getProvider(type);
        return provider.generateAnalytics(symbol);
    }
    
    /**
     * Generate analytics data using the appropriate provider with additional parameters
     * @param type The analytics type
     * @param symbol The symbol to generate analytics for
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     * @param <T> The type of analytics data returned
     */
    @SuppressWarnings("unchecked")
    public <T> T generateAnalytics(AnalyticsType type, String symbol, Object... params) {
        AnalyticsProvider<T> provider = getProvider(type);
        return provider.generateAnalytics(symbol, params);
    }
}
