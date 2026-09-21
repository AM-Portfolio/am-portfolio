package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads the authenticated owner's portfolios and merges BROKER books for All-Portfolios analytics.
 */
@Service
@RequiredArgsConstructor
public class AggregatePortfolioLoader {

    private final PortfolioService portfolioService;
    private final AggregatePortfolioFactory aggregatePortfolioFactory;

    public PortfolioModelV1 loadMerged(String userId) {
        return aggregatePortfolioFactory.mergeBrokerBooks(
                userId, portfolioService.getPortfoliosByUserId(userId));
    }
}
