package com.portfolio.basket.scheduler;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.basket.service.UserPortfolioProvider;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketRecommendationScheduler {

    private final BasketEngineService basketService;
    private final BasketCatalogService basketCatalogService;
    private final UserPortfolioProvider userProvider;

    @Value("${basket.scheduler.parallel-users:4}")
    private int parallelUsers;

    // Run Daily at 6 PM
    @Scheduled(cron = "0 0 18 * * ?", zone = "Asia/Kolkata")
    public void runDailyRecommendation() {
        log.info("⏰ Starting Daily Basket Recommendation Job...");

        List<String> users = userProvider.getAllActiveUsers();
        log.info("found {} users to process", users.size());

        int workers = Math.max(1, parallelUsers);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            for (String userId : users) {
                executor.submit(() -> processUser(userId));
            }
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.HOURS)) {
                log.warn("Basket recommendation job did not finish within 2 hours");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        log.info("✅ Daily Basket Recommendation Job Completed.");
    }

    private void processUser(String userId) {
        try {
            List<EquityHoldings> holdings = userProvider.getUserHoldings(userId);
            if (holdings == null || holdings.isEmpty())
                return;

            String defaultQuery = basketCatalogService.resolveDefaultQuery();
            List<BasketOpportunity> opportunities = basketService.findOpportunities(holdings, defaultQuery);

            if (!opportunities.isEmpty()) {
                log.info("User {}: Found {} opportunities", userId, opportunities.size());
                opportunities.forEach(op -> log.debug("   - Opportunity: {} (Score: {}%)", op.getEtfName(),
                        String.format("%.1f", op.getMatchScore())));
            }
        } catch (Exception e) {
            log.error("Error processing user {}", userId, e);
        }
    }
}
