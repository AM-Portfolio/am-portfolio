package com.portfolio.app.provider;

import com.portfolio.basket.service.UserPortfolioProvider;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPortfolioProviderImpl implements UserPortfolioProvider {

    private final PortfolioHoldingsService portfolioHoldingsService;

    @Override
    public List<String> getAllActiveUsers() {
        // TODO: Implement logic to fetch all active users from DB/Service
        // For now, returning empty list to allow application startup
        log.warn("getAllActiveUsers is not fully implemented yet. Returning empty list.");
        return Collections.emptyList();
    }

    @Override
    public List<EquityHoldings> getUserHoldings(String userId) {
        try {
            PortfolioHoldings holdings = portfolioHoldingsService.getPortfolioHoldings(userId, null);
            if (holdings != null && holdings.getEquityHoldings() != null) {
                return holdings.getEquityHoldings();
            }
        } catch (Exception e) {
            log.error("Error fetching holdings for user {}: {}", userId, e.getMessage());
        }
        return Collections.emptyList();
    }
}
