package com.portfolio.analytics.service.providers.portfolio;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

/**
 * Interface for portfolio analytics providers that generate analytics data for portfolios
 * @param <T> The type of analytics data returned
 */
public interface PortfolioAnalyticsProvider<T> {
    
    /**
     * Get the type of analytics this provider generates
     * @return The analytics type identifier
     */
    AnalyticsType getType();
    
    /**
     * Generate analytics data for the given portfolio with additional parameters
     * @param portfolioId The portfolio ID to generate analytics for
     * @param params Additional parameters for analytics generation
     * @return The analytics data
     */
    T generateAnalytics(AdvancedAnalyticsRequest request);
}
