package com.portfolio.service.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asynchronous service that reconstructs true historical portfolio snapshots
 * for returning users. Uses real daily closing prices from the Market Data API
 * instead of flat carry-forward, ensuring accurate chart data for users who
 * return after long periods of inactivity (weeks, months, or even a year).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotCatchUpService {

    // Hard cap: never backfill more than maxBackfillDays to protect the system from
    // extreme edge cases (e.g. user inactive for 5 years).
    @Value("${app.scheduler.snapshot.max-backfill-days:365}")
    private int maxBackfillDays;

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioService portfolioService;
    private final MarketDataApiClient marketDataApiClient;

    /**
     * Triggered from UserLoginConsumerService after a login event.
     * Runs asynchronously so the user's login is NOT blocked.
     * Uses the named "taskExecutor" bean which propagates MDC trace context.
     */
    @Async("taskExecutor")
    public void triggerCatchUp(String userId) {
        log.info("[CatchUp] Starting async historical snapshot catch-up for userId={}", userId);
        try {
            LocalDate today = LocalDate.now();

            // --- STEP 1: Determine the gap ---
            List<PortfolioSnapshotDocument> latest = portfolioSnapshotRepository
                    .findByUserIdOrderBySnapshotDateDesc(userId, PageRequest.of(0, 1));

            if (latest.isEmpty()) {
                log.info("[CatchUp] No existing snapshots for userId={}. EOD scheduler will create the first one.", userId);
                return;
            }

            LocalDate lastSnapshotDate = latest.get(0).getSnapshotDate();

            // Gap is from the day after the last snapshot up to (not including) today.
            // If the last snapshot is yesterday or today, we're up to date.
            if (!lastSnapshotDate.isBefore(today.minusDays(1))) {
                log.info("[CatchUp] Snapshots are up to date for userId={}. Last: {}", userId, lastSnapshotDate);
                return;
            }

            LocalDate backfillStart = lastSnapshotDate.plusDays(1);
            // Enforce the max backfill window
            LocalDate hardCap = today.minusDays(maxBackfillDays);
            if (backfillStart.isBefore(hardCap)) {
                log.info("[CatchUp] Gap is larger than {} days for userId={}. Capping backfill start to {}.",
                        maxBackfillDays, userId, hardCap);
                backfillStart = hardCap;
            }

            log.info("[CatchUp] Gap detected for userId={}. Backfilling from {} to {} ({} days).",
                    userId, backfillStart, today.minusDays(1),
                    java.time.temporal.ChronoUnit.DAYS.between(backfillStart, today));
            PortfolioSnapshotDocument lastSnapshot = latest.get(0);

            // --- STEP 2: Fetch user's raw holdings from the nested portfolio entries ---
            // Holdings are now embedded inside each PortfolioSnapshotEntry, not in a separate top-level array.
            Map<String, List<HoldingInfo>> portfolioHoldings = new HashMap<>();
            Set<String> allSymbols = new HashSet<>();

            if (lastSnapshot.getPortfolios() != null && !lastSnapshot.getPortfolios().isEmpty()
                    && lastSnapshot.getPortfolios().stream().anyMatch(e -> e.getHoldings() != null && !e.getHoldings().isEmpty())) {

                // Primary path: read from nested holdings inside each portfolio entry
                for (com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry entry : lastSnapshot.getPortfolios()) {
                    if (entry.getHoldings() == null || entry.getHoldings().isEmpty()) continue;
                    String pId = entry.getPortfolioId();
                    String brokerStr = entry.getBrokerType();
                    portfolioHoldings.putIfAbsent(pId, new ArrayList<>());
                    for (com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem item : entry.getHoldings()) {
                        if (item.getSymbol() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                            portfolioHoldings.get(pId).add(new HoldingInfo(
                                    item.getSymbol(),
                                    item.getQuantity(),
                                    item.getAvgBuyPrice() != null ? item.getAvgBuyPrice() : 0.0,
                                    brokerStr,
                                    entry.getPortfolioName()
                            ));
                            allSymbols.add(item.getSymbol());
                        }
                    }
                }

            } else {
                log.warn("[CatchUp] No nested holdings found in last snapshot for userId={}. Falling back to current holdings from DB.", userId);

                // Fallback path: read from current portfolio DB records
                List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
                if (portfolios == null || portfolios.isEmpty()) {
                    log.warn("[CatchUp] No portfolios found for userId={}. Aborting catch-up.", userId);
                    return;
                }

                for (PortfolioModelV1 portfolio : portfolios) {
                    if (portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) continue;
                    String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : null;
                    if (portfolioId == null) continue;
                    String brokerStr = portfolio.getBrokerType() != null ? portfolio.getBrokerType().name() : null;

                    List<HoldingInfo> holdingInfos = portfolio.getEquityModels().stream()
                            .filter(e -> e.getSymbol() != null && e.getQuantity() != null && e.getQuantity() > 0)
                            .map(e -> new HoldingInfo(
                                    e.getSymbol(),
                                    e.getQuantity(),
                                    e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice() : 0.0,
                                    brokerStr,
                                    portfolio.getName()))
                            .collect(Collectors.toList());

                    if (!holdingInfos.isEmpty()) {
                        portfolioHoldings.put(portfolioId, holdingInfos);
                        holdingInfos.forEach(h -> allSymbols.add(h.symbol));
                    }
                }
            }

            if (allSymbols.isEmpty()) {
                log.warn("[CatchUp] No valid equity holdings (with symbols) found for userId={}. Aborting.", userId);
                return;
            }

            log.info("[CatchUp] Found {} unique symbols across {} portfolios for userId={}.",
                    allSymbols.size(), portfolioHoldings.size(), userId);

            // --- STEP 3: Fetch historical daily prices from Market Data API (SINGLE batch call) ---
            String symbolsParam = String.join(",", allSymbols);
            HistoricalDataRequest request = HistoricalDataRequest.builder()
                    .symbols(symbolsParam)
                    .fromDate(backfillStart != null ? backfillStart.toString() : null)
                    .toDate(today.minusDays(1).toString()) // up to yesterday (market closed)
                    .interval("day")
                    .forceRefresh(false)
                    .build();

            HistoricalDataResponseWrapper histResponse;
            try {
                histResponse = marketDataApiClient.getHistoricalData(request).block();
            } catch (Exception e) {
                log.error("[CatchUp] Failed to fetch historical data for userId={} symbols={}. Aborting.", userId, symbolsParam, e);
                return;
            }

            if (histResponse == null || histResponse.getData() == null || histResponse.getData().isEmpty()) {
                log.warn("[CatchUp] Market Data API returned empty response for userId={}. Aborting.", userId);
                return;
            }

            // --- STEP 4: Build a date → symbol → closePrice lookup map ---
            // Key1 = LocalDate, Key2 = symbol, Value = close price
            // This is O(1) lookup during the date loop — extremely fast.
            Map<LocalDate, Map<String, Double>> priceLookup = new HashMap<>();

            for (Map.Entry<String, com.portfolio.marketdata.model.HistoricalDataResponse> entry
                    : histResponse.getData().entrySet()) {
                String symbol = entry.getKey();
                com.portfolio.marketdata.model.HistoricalDataResponse symbolData = entry.getValue();
                if (symbolData == null || symbolData.getData() == null
                        || symbolData.getData().getDataPoints() == null) {
                    continue;
                }
                for (com.am.common.investment.model.historical.OHLCVTPoint point
                        : symbolData.getData().getDataPoints()) {
                    if (point == null || point.getClose() == null || point.getTime() == null) {
                        continue;
                    }
                    LocalDate date = point.getTime().toLocalDate();
                    priceLookup.computeIfAbsent(date, k -> new HashMap<>()).put(symbol, point.getClose());
                }
            }

            log.info("[CatchUp] Built price lookup map for {} trading days for userId={}.", priceLookup.size(), userId);

            // --- STEP 5: Load existing snapshot dates in bulk (single DB query, idempotency) ---
            Set<LocalDate> existingDates = portfolioSnapshotRepository
                    .findByUserIdAndSnapshotDateBetween(userId, backfillStart, today.minusDays(1))
                    .stream()
                    .map(PortfolioSnapshotDocument::getSnapshotDate)
                    .collect(Collectors.toSet());

            // --- STEP 6: Loop through each missing day and reconstruct snapshots ---
            List<PortfolioSnapshotDocument> batch = new ArrayList<>();

            // Track "last known price" per symbol to carry-forward over weekends/holidays
            Map<String, Double> lastKnownPrice = new HashMap<>();

            for (LocalDate date = backfillStart; date.isBefore(today); date = date.plusDays(1)) {
                // Skip dates that already have a snapshot (idempotency)
                if (existingDates.contains(date)) {
                    log.debug("[CatchUp] Snapshot already exists for date={}, userId={}. Skipping.", date, userId);
                    continue;
                }

                // Update last known price for each symbol if today is a trading day
                Map<String, Double> dayPrices = priceLookup.getOrDefault(date, Collections.emptyMap());
                lastKnownPrice.putAll(dayPrices);

                // If we have no price data at all yet (e.g. API has no data for this range),
                // use the last snapshot's close as the final fallback.
                // This ensures even weekends before any trading data still produce valid snapshots.
                if (lastKnownPrice.isEmpty()) {
                    log.debug("[CatchUp] No price data available yet for date={}. Skipping this date.", date);
                    continue;
                }

                // Calculate per-portfolio OHLC entries
                List<PortfolioSnapshotEntry> entries = new ArrayList<>();
                double totalWealth = 0.0;
                double totalInvestment = 0.0;

                for (Map.Entry<String, List<HoldingInfo>> portEntry : portfolioHoldings.entrySet()) {
                    String portfolioId = portEntry.getKey();
                    List<HoldingInfo> holdings = portEntry.getValue();

                    double portValue = 0.0;
                    double portInvestment = 0.0;

                    for (HoldingInfo h : holdings) {
                        double price = lastKnownPrice.getOrDefault(h.symbol, h.avgBuyPrice);
                        portValue += h.quantity * price;
                        portInvestment += h.quantity * h.avgBuyPrice;
                    }

                    double portGainLoss = portValue - portInvestment;
                    double portGainLossPct = portInvestment > 0 ? (portGainLoss / portInvestment) * 100.0 : 0.0;

                    // Build nested holdings for the catch-up entry
                    List<com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem> catchUpHoldings = holdings.stream()
                            .map(h -> com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem.builder()
                                    .symbol(h.symbol)
                                    .quantity(h.quantity)
                                    .avgBuyPrice(h.avgBuyPrice)
                                    .build())
                            .collect(Collectors.toList());

                    String brokerStr = holdings.isEmpty() ? null : holdings.get(0).brokerType;
                    String portfolioName = holdings.isEmpty() ? null : holdings.get(0).portfolioName;

                    // For catch-up snapshots: open = close = high = low (daily close value)
                    // We don't have intra-day data for historical reconstruction.
                    entries.add(PortfolioSnapshotEntry.builder()
                            .portfolioId(portfolioId)
                            .portfolioName(portfolioName)
                            .brokerType(brokerStr)
                            .open(portValue)
                            .high(portValue)
                            .low(portValue)
                            .close(portValue)
                            .totalInvestment(portInvestment)
                            .totalGainLoss(portGainLoss)
                            .totalGainLossPercentage(portGainLossPct)
                            .holdings(catchUpHoldings)
                            .build());

                    totalWealth += portValue;
                    totalInvestment += portInvestment;
                }

                if (entries.isEmpty()) {
                    continue;
                }

                double totalGainLoss = totalWealth - totalInvestment;
                double totalGainLossPct = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100.0 : 0.0;

                String snapshotId = UUID.randomUUID().toString();
                batch.add(PortfolioSnapshotDocument.builder()
                        .id(snapshotId)
                        .snapshotId(snapshotId)
                        .userId(userId)
                        .snapshotDate(date)
                        .totalUserWealth(totalWealth)
                        .totalUserInvestment(totalInvestment)
                        .totalUserGainLoss(totalGainLoss)
                        .totalUserGainLossPercentage(totalGainLossPct)
                        .portfolios(entries)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            // --- STEP 7: Bulk insert all reconstructed snapshots in a single DB call ---
            if (!batch.isEmpty()) {
                portfolioSnapshotRepository.saveAll(batch);
                log.info("[CatchUp] ✅ Completed historical catch-up for userId={}. Saved {} snapshots (from {} to {}).",
                        userId, batch.size(), backfillStart, today.minusDays(1));
            } else {
                log.info("[CatchUp] No new snapshots to save for userId={}. All dates already exist.", userId);
            }

        } catch (Exception e) {
            log.error("[CatchUp] Unexpected failure during snapshot catch-up for userId={}", userId, e);
        }
    }

    /**
     * Lightweight inner record to hold raw holding data without requiring
     * live market enrichment. Avoids dependency on EquityHoldings domain model.
     */
    private static class HoldingInfo {
        final String symbol;
        final double quantity;
        final double avgBuyPrice;
        final String brokerType;
        final String portfolioName;

        HoldingInfo(String symbol, double quantity, double avgBuyPrice, String brokerType, String portfolioName) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.avgBuyPrice = avgBuyPrice;
            this.brokerType = brokerType;
            this.portfolioName = portfolioName;
        }
    }
}
