package com.portfolio.analytics.intelligence;

/**
 * Stable cache / in-flight keys for All-Portfolios aggregate intelligence.
 * Never reuse a real portfolio UUID.
 */
public final class AggregatePortfolioKeys {

    public static final String RESPONSE_PORTFOLIO_ID = "ALL";

    private AggregatePortfolioKeys() {}

    public static String cacheKey(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required for aggregate cache key");
        }
        return "user:" + userId.trim() + ":all";
    }
}
