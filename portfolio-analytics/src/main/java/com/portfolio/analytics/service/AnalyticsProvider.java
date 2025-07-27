package com.portfolio.analytics.service;

/**
 * Interface for analytics providers that generate analytics data
 * @param <T> The type of analytics data returned
 */
public interface AnalyticsProvider<T> {
    
    /**
     * Get the type of analytics this provider generates
     * @return The analytics type identifier
     */
    AnalyticsType getType();
    
    /**
     * Generate analytics data for the given symbol
     * @param symbol The symbol to generate analytics for (e.g., index symbol)
     * @return The analytics data
     */
    T generateAnalytics(String symbol);
    
    /**
     * Generate analytics data for the given symbol with additional parameters
     * @param symbol The symbol to generate analytics for
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     */
    T generateAnalytics(String symbol, Object... params);
}
