package com.portfolio.analytics.service;

import com.portfolio.model.analytics.request.TimeFrameRequest;

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
    
    /**
     * Generate analytics data for the given symbol with time frame parameters
     * @param symbol The symbol to generate analytics for (e.g., index symbol)
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return The analytics data
     */
    T generateAnalytics(String symbol, TimeFrameRequest timeFrameRequest);
    
    /**
     * Generate analytics data for the given symbol with time frame parameters and additional parameters
     * @param symbol The symbol to generate analytics for
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     */
    T generateAnalytics(String symbol, TimeFrameRequest timeFrameRequest, Object... params);
}
