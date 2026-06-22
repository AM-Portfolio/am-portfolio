package com.portfolio.service.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.am.common.amcommondata.service.PortfolioSnapshotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioHistoryScheduler {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioSnapshotService portfolioSnapshotService;

    // Runs every day at 16:00 (4 PM) IST (Asia/Kolkata)
    // Cron: Second, Minute, Hour, Day of Month, Month, Day of Week
    @Scheduled(cron = "0 0 16 * * *", zone = "Asia/Kolkata")
    public void runEndOfDayJob() {
        log.info("Starting End-of-Day Portfolio History Job at {}", LocalDateTime.now());

        try {
            List<String> userIds = portfolioService.getAllUserIds();
            log.info("Found {} users for history generation", userIds.size());

            int processed = 0;
            int errors = 0;

            for (String userId : userIds) {
                try {
                    // Force enrichment (true) to calculate using closing prices and cache to Redis
                    // This acts as our "Daily Snapshot"
                    PortfolioHoldings holdings = portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
                    
                    if (holdings != null && holdings.getEquityHoldings() != null) {
                        double totalInvestment = holdings.getEquityHoldings().stream()
                                .filter(h -> h.getInvestmentCost() != null)
                                .mapToDouble(com.portfolio.model.portfolio.EquityHoldings::getInvestmentCost)
                                .sum();
                                
                        double totalValue = holdings.getEquityHoldings().stream()
                                .filter(h -> h.getCurrentValue() != null)
                                .mapToDouble(com.portfolio.model.portfolio.EquityHoldings::getCurrentValue)
                                .sum();
                                
                        double totalGainLoss = totalValue - totalInvestment;
                        double totalGainLossPercentage = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100 : 0.0;

                        // Save snapshot to MongoDB
                        String portfolioId = holdings.getPortfolioId();
                        if (portfolioId != null) {
                            portfolioSnapshotService.saveSnapshot(portfolioId, userId, totalValue, totalInvestment, totalGainLoss, totalGainLossPercentage);
                        } else {
                            log.warn("PortfolioHistoryScheduler - portfolioId is null for userId: {}. Snapshot skipped.", userId);
                        }
                    }
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to generate history for user: {}", userId, e);
                    errors++;
                }
            }

            log.info("Completed End-of-Day Job. Processed: {}, Errors: {}", processed, errors);

        } catch (Exception e) {
            log.error("Critical failure in End-of-Day Job", e);
        }
    }
}
