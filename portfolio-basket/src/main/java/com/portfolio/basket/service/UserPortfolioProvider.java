package com.portfolio.basket.service;

import com.portfolio.model.portfolio.EquityHoldings;
import java.util.List;
import java.util.Map;

public interface UserPortfolioProvider {
    /**
     * Fetch all active users who are eligible for basket recommendations.
     * 
     * @return List of User IDs
     */
    List<String> getAllActiveUsers();

    /**
     * Fetch holdings for a specific user.
     * 
     * @param userId
     * @return List of EquityHoldings
     */
    List<EquityHoldings> getUserHoldings(String userId);
}
