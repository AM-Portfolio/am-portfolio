package com.portfolio.service.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.portfolio.PortfolioHoldingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioHistoryScheduler {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;

    // Runs every day at 17:00 (5 PM) Server Time
    // Cron: Second, Minute, Hour, Day of Month, Month, Day of Week
    @Scheduled(cron = "0 0 17 * * *")
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
                    portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
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
