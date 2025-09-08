package com.portfolio.analytics.service.providers.index;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

/**
 * Interface for analytics providers that generate analytics data
 * @param <T> The type of analytics data returned
 */
public interface IndexAnalyticsProvider<T> {
    
    /**
     * Get the type of analytics this provider generates
     * @return The analytics type identifier
     */
    AnalyticsType getType();
    
    /**
     * Generate analytics data for the given symbol with additional parameters
     * @param symbol The symbol to generate analytics for
     * @param request Additional parameters for analytics generation
     * @return The analytics data
     */
    T generateAnalytics(AdvancedAnalyticsRequest request);
    
}
