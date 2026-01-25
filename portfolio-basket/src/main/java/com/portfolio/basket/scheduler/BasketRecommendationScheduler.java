package com.portfolio.basket.scheduler;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.basket.service.UserPortfolioProvider;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketRecommendationScheduler {

    private final BasketEngineService basketService;
    private final UserPortfolioProvider userProvider;

    // Run Daily at 6 PM
    @Scheduled(cron = "0 0 18 * * ?")
    public void runDailyRecommendation() {
        log.info("⏰ Starting Daily Basket Recommendation Job...");

        // 1. Refresh ETF Data (Loaded from Mock in Phase 2)
        // basketService.refreshEtfData();

        // 2. Fetch All Users
        List<String> users = userProvider.getAllActiveUsers();
        log.info("found {} users to process", users.size());

        // 3. Process
        users.stream().forEach(this::processUser);

        log.info("✅ Daily Basket Recommendation Job Completed.");
    }

    private void processUser(String userId) {
        try {
            List<EquityHoldings> holdings = userProvider.getUserHoldings(userId);
            if (holdings == null || holdings.isEmpty())
                return;

            List<BasketOpportunity> opportunities = basketService.findOpportunities(holdings, null);

            if (!opportunities.isEmpty()) {
                log.info("User {}: Found {} opportunities", userId, opportunities.size());
                // TODO: Save to Recommendation Repository or Notify User
                opportunities.forEach(op -> log.debug("   - Opportunity: {} (Score: {}%)", op.getEtfName(),
                        String.format("%.1f", op.getMatchScore())));
            }
        } catch (Exception e) {
            log.error("Error processing user {}", userId, e);
        }
    }
}
