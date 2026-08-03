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
import com.portfolio.service.portfolio.PortfolioIntradayService;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.am.common.investment.model.historical.OHLCVTPoint;

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
    private final MarketDataApiClient marketDataApiClient;
    
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private PortfolioIntradayService portfolioIntradayService;
    
    private final com.am.common.amcommondata.repository.portfolio.PortfolioIntradaySessionRepository intradaySessionRepository;

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

            // Collect all symbols and holdings first
            Set<String> allSymbols = new HashSet<>();
            Map<String, PortfolioHoldings> userHoldings = new HashMap<>();

            for (PortfolioModelV1 portfolio : portfolios) {
                String portfolioId = portfolio.getId().toString();
                PortfolioHoldings enrichedHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);
                if (enrichedHoldings != null && enrichedHoldings.getEquityHoldings() != null) {
                    userHoldings.put(portfolioId, enrichedHoldings);
                    for (EquityHoldings h : enrichedHoldings.getEquityHoldings()) {
                        if (h.getSymbol() != null) {
                            allSymbols.add(h.getSymbol());
                        }
                    }
                }
            }

            // Fetch historical closing price for 'date'
            Map<String, Double> closingPrices = new HashMap<>();
            if (!allSymbols.isEmpty()) {
                String symbolsParam = String.join(",", allSymbols);
                HistoricalDataRequest request = HistoricalDataRequest.builder()
                        .symbols(symbolsParam)
                        .fromDate(date.toString())
                        .toDate(date.toString())
                        .interval("day")
                        .forceRefresh(false)
                        .build();

                try {
                    HistoricalDataResponseWrapper histResponse = marketDataApiClient.getHistoricalData(request).block();
                    if (histResponse != null && histResponse.getData() != null) {
                        for (Map.Entry<String, HistoricalDataResponse> entry : histResponse.getData().entrySet()) {
                            String symbol = entry.getKey();
                            HistoricalDataResponse symbolData = entry.getValue();
                            if (symbolData != null && symbolData.getData() != null && symbolData.getData().getDataPoints() != null) {
                                for (OHLCVTPoint point : symbolData.getData().getDataPoints()) {
                                    if (point != null && point.getClose() != null) {
                                        closingPrices.put(symbol, point.getClose());
                                        break; // Only need the one point for the requested date
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch historical closing prices for date={} symbols={}", date, symbolsParam, e);
                }
            }

            for (PortfolioModelV1 portfolio : portfolios) {
                String portfolioId = portfolio.getId().toString();
                PortfolioHoldings enrichedHoldings = userHoldings.get(portfolioId);

                if (enrichedHoldings == null || enrichedHoldings.getEquityHoldings() == null) continue;

                double portValue = 0.0;
                double portInvestment = 0.0;
                List<HoldingSnapshotItem> snapshotHoldings = new ArrayList<>();

                for (EquityHoldings h : enrichedHoldings.getEquityHoldings()) {
                    double defaultPrice = h.getCurrentPrice() != null ? h.getCurrentPrice() : h.getAverageBuyingPrice();
                    double price = closingPrices.getOrDefault(h.getSymbol(), defaultPrice);
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

            // --- Persist Completed Intraday Sessions (ADR-001-D4) ---
            if (date.equals(LocalDate.now())) {
                persistIntradaySession(userId, null, date); // Aggregate portfolio
                for (PortfolioModelV1 portfolio : portfolios) {
                    persistIntradaySession(userId, portfolio.getId().toString(), date); // Individual portfolios
                }
            }

        } catch (Exception e) {
            log.error("Failed to generate history for user: {} on date: {}", userId, date, e);
        }
    }

    private void persistIntradaySession(String userId, String portfolioId, LocalDate date) {
        try {
            var dataPoints = portfolioIntradayService.getIntraday(userId, portfolioId);
            boolean hasRealVariation = dataPoints != null && dataPoints.stream()
                    .mapToDouble(dp -> dp.getTotalWealth()).distinct().count() > 2;
            if (dataPoints != null && dataPoints.size() > 10 && hasRealVariation) { // Only save if substantial real curve
                List<com.am.common.amcommondata.document.portfolio.PortfolioIntradaySessionDocument.SessionDataPoint> sessionPoints = new ArrayList<>();
                for (com.portfolio.model.portfolio.IntradayDataPoint dp : dataPoints) {
                    sessionPoints.add(new com.am.common.amcommondata.document.portfolio.PortfolioIntradaySessionDocument.SessionDataPoint(
                            dp.getTimestamp(), dp.getTotalWealth(), dp.getChangeFromOpen(), dp.getChangeFromOpenPct()
                    ));
                }
                
                String pId = portfolioId != null ? portfolioId : "";
                var sessionDoc = com.am.common.amcommondata.document.portfolio.PortfolioIntradaySessionDocument.builder()
                        .userId(userId)
                        .portfolioId(pId)
                        .sessionDate(date)
                        .baselineWealth(dataPoints.get(0).getTotalWealth())
                        .dataPoints(sessionPoints)
                        .createdAt(LocalDateTime.now())
                        .build();

                // If already exists for this date, delete first or upsert
                intradaySessionRepository.findFirstByUserIdAndPortfolioIdOrderBySessionDateDesc(userId, pId)
                        .filter(doc -> doc.getSessionDate().equals(date))
                        .ifPresent(doc -> sessionDoc.setId(doc.getId())); // Update existing

                intradaySessionRepository.save(sessionDoc);
                log.info("Persisted intraday session for user={} portfolioId={} date={} points={}", userId, pId, date, sessionPoints.size());
            }
        } catch (Exception e) {
            log.warn("Failed to persist intraday session for user={} portfolioId={}: {}", userId, portfolioId, e.getMessage());
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
    @Deprecated
    public void backfillSnapshotsAsync(String userId, int daysBack) {
        log.warn("[ASYNC] DEPRECATED: backfillSnapshotsAsync called for user={}. Use SnapshotCatchUpService instead.", userId);
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
