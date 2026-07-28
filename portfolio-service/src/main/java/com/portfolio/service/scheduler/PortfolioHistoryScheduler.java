package com.portfolio.service.scheduler;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.portfolio.PortfolioHoldingsService;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import java.util.ArrayList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioHistoryScheduler {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioSnapshotService portfolioSnapshotService;
    private final com.am.common.amcommondata.service.price.StockPriceMongoService stockPriceMongoService;
    private final com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;

    // Runs every day at 17:00 (5 PM) IST (Asia/Kolkata)
    // Cron: Second, Minute, Hour, Day of Month, Month, Day of Week
    @Scheduled(cron = "0 0 17 * * *", zone = "Asia/Kolkata")
    public void runEndOfDayJob() {
        log.info("Starting End-of-Day Portfolio History Job at {}", LocalDateTime.now());

        // Part 1: Seed stock_price_history from stock_prices_cache
        try {
            var allPrices = stockPriceMongoService.findAll();
            if (allPrices != null && !allPrices.isEmpty()) {
                log.info("Seeding stock price history with {} ticks from stock prices cache", allPrices.size());
                stockPriceHistoryMongoService.saveAll(allPrices);
            }
        } catch (Exception e) {
            log.warn("Failed to seed price history ticks: {}", e.getMessage());
        }

        // Part 2: Generate snapshots for all users
        try {
            List<String> userIds = portfolioService.getAllUserIds();
            log.info("Found {} users for history generation", userIds.size());

            int processed = 0;
            LocalDate today = LocalDate.now();

            for (String userId : userIds) {
                runEndOfDayJobForUserAndDate(userId, today);
                processed++;
            }

            log.info("Completed End-of-Day Job. Processed users: {}", processed);

        } catch (Exception e) {
            log.error("Critical failure in End-of-Day Job", e);
        }
    }

    public void runEndOfDayJobForUserAndDate(String userId, LocalDate date) {
        try {
            List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
            List<PortfolioSnapshotEntry> entries = new ArrayList<>();
            double totalWealth = 0.0;
            double totalInvestment = 0.0;

            for (PortfolioModelV1 portfolio : portfolios) {
                String portfolioId = portfolio.getId().toString();
                PortfolioHoldings enrichedHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);

                if (enrichedHoldings == null || enrichedHoldings.getEquityHoldings() == null) continue;

                double portValue = 0.0;
                double portInvestment = 0.0;
                List<HoldingSnapshotItem> snapshotHoldings = new ArrayList<>();

                for (EquityHoldings h : enrichedHoldings.getEquityHoldings()) {
                    double price = h.getCurrentPrice() != null ? h.getCurrentPrice() : h.getAverageBuyingPrice();
                    double value = h.getQuantity() * price;
                    double cost = h.getQuantity() * h.getAverageBuyingPrice();
                    
                    portValue += value;
                    portInvestment += cost;
                    
                    snapshotHoldings.add(HoldingSnapshotItem.builder()
                        .symbol(h.getSymbol())
                        .isin(h.getIsin())
                        .quantity(h.getQuantity())
                        .avgBuyPrice(h.getAverageBuyingPrice())
                        .build());
                }
                
                double portGainLoss = portValue - portInvestment;
                double portGainLossPct = portInvestment > 0 ? (portGainLoss / portInvestment) * 100.0 : 0.0;

                entries.add(PortfolioSnapshotEntry.builder()
                        .portfolioId(portfolioId)
                        .portfolioName(portfolio.getName())
                        .brokerType(portfolio.getBrokerType() != null ? portfolio.getBrokerType().name() : null)
                        .open(portValue)
                        .high(portValue)
                        .low(portValue)
                        .close(portValue)
                        .totalInvestment(portInvestment)
                        .totalGainLoss(portGainLoss)
                        .totalGainLossPercentage(portGainLossPct)
                        .holdings(snapshotHoldings)
                        .build());
                        
                totalWealth += portValue;
                totalInvestment += portInvestment;
            }

            if (!entries.isEmpty()) {
                double totalGainLoss = totalWealth - totalInvestment;
                double totalGainLossPct = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100.0 : 0.0;

                portfolioSnapshotService.saveUserSnapshot(userId, totalWealth, totalInvestment, totalGainLoss, totalGainLossPct, entries, date);
            }
        } catch (Exception e) {
            log.error("Failed to generate history for user: {} on date: {}", userId, date, e);
        }
    }

    /**
     * Async wrapper used by the manual trigger REST endpoint.
     * Returns immediately so Cloudflare does not time out (524).
     * The actual heavy work runs on Spring's async task executor thread pool.
     */
    @Async
    public void runEndOfDayJobAsync() {
        log.info("[ASYNC] Manual EOD snapshot trigger received — starting job on background thread");
        runEndOfDayJob();
    }

    @Async
    public void runEndOfDayJobForUserAndDateAsync(String userId, LocalDate date) {
        log.info("[ASYNC] Manual snapshot trigger for user={} on date={} starting", userId, date);
        runEndOfDayJobForUserAndDate(userId, date);
    }

    @Async
    public void backfillSnapshotsAsync(String userId, int daysBack) {
        log.info("[ASYNC] Backfill snapshots trigger for user={} for last {} days starting", userId, daysBack);
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= daysBack; i++) {
            LocalDate targetDate = today.minusDays(i);
            // Skip weekends
            if (targetDate.getDayOfWeek() == DayOfWeek.SATURDAY || targetDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            log.info("Backfilling snapshot for user={} on date={}", userId, targetDate);
            runEndOfDayJobForUserAndDate(userId, targetDate);
        }
        log.info("[ASYNC] Backfill snapshots completed for user={}", userId);
    }
}
