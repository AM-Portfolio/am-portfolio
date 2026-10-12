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
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.model.util.InstrumentKeys;

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
    private final MarketDataApiConfig marketDataApiConfig;

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

            LocalDate backfillStart;
            PortfolioSnapshotDocument lastSnapshot = null;

            if (latest.isEmpty()) {
                log.info("[CatchUp] No existing snapshots for userId={}. Bootstrapping initial historical snapshots (up to 30 days).", userId);
                backfillStart = today.minusDays(30);
            } else {
                lastSnapshot = latest.get(0);
                LocalDate lastSnapshotDate = lastSnapshot.getSnapshotDate();

                // Gap is from the day after the last snapshot up to (not including) today.
                boolean tipFresh = !lastSnapshotDate.isBefore(today.minusDays(1));
                // Day-P&L freeze needs ≥2 prior sessions; fill holes in the last ~10 calendar days.
                List<PortfolioSnapshotDocument> recentSnaps = portfolioSnapshotRepository
                        .findByUserIdAndSnapshotDateBetween(userId, today.minusDays(10), today);
                long recentSessions = recentSnaps.size();
                boolean freezeMissing = recentSnaps.stream().anyMatch(SnapshotCatchUpService::needsSessionDayFreezeRepair);
                if (tipFresh && recentSessions >= 2 && !freezeMissing) {
                    log.info("[CatchUp] Snapshots are up to date for userId={}. Last: {} recentSessions={}",
                            userId, lastSnapshotDate, recentSessions);
                    return;
                }
                if (tipFresh || freezeMissing) {
                    log.info("[CatchUp] Tip fresh/gaps/freeze-repair recentSessions={} freezeMissing={} userId={}",
                            recentSessions, freezeMissing, userId);
                    backfillStart = today.minusDays(10);
                } else {
                    backfillStart = lastSnapshotDate.plusDays(1);
                }
                // Enforce the max backfill window
                LocalDate hardCap = today.minusDays(maxBackfillDays);
                if (backfillStart.isBefore(hardCap)) {
                    log.info("[CatchUp] Gap is larger than {} days for userId={}. Capping backfill start to {}.",
                            maxBackfillDays, userId, hardCap);
                    backfillStart = hardCap;
                }
            }

            log.info("[CatchUp] Gap detected for userId={}. Backfilling from {} to {} ({} days).",
                    userId, backfillStart, today.minusDays(1),
                    java.time.temporal.ChronoUnit.DAYS.between(backfillStart, today));

            // --- STEP 2: Fetch user's raw holdings from the nested portfolio entries ---
            // Holdings are now embedded inside each PortfolioSnapshotEntry, not in a separate top-level array.
            Map<String, List<HoldingInfo>> portfolioHoldings = new HashMap<>();
            Set<String> allSymbols = new HashSet<>();

            if (lastSnapshot != null && lastSnapshot.getPortfolios() != null && !lastSnapshot.getPortfolios().isEmpty()
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
            // Cash segment from market-data.api.equity-instrument-prefix (default NSE_EQ).
            String equityPrefix = marketDataApiConfig.getEquityInstrumentPrefix();
            var passthrough = InstrumentKeys.parsePassthroughPrefixes(
                    marketDataApiConfig.getPassthroughInstrumentPrefixes());
            String symbolsParam = allSymbols.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> InstrumentKeys.toInstrumentKey(s, equityPrefix, passthrough))
                    .distinct()
                    .collect(Collectors.joining(","));
            HistoricalDataRequest request = HistoricalDataRequest.builder()
                    .symbols(symbolsParam)
                    .fromDate(backfillStart != null ? backfillStart.toString() : null)
                    .toDate(today.minusDays(1).toString()) // up to yesterday (market closed)
                    // am-market flow logs / Upstox path expect 1D (alias of day).
                    .interval("1D")
                    .instrumentType("EQ")
                    // Dig Mongo hist can be cold for these symbols; force provider fill.
                    .forceRefresh(true)
                    .build();

            log.info("[CatchUp] Requesting hist userId={} symbols={} from={} to={} interval=1D forceRefresh=true",
                    userId, symbolsParam, request.getFromDate(), request.getToDate());

            HistoricalDataResponseWrapper histResponse;
            try {
                histResponse = marketDataApiClient.getHistoricalData(request).block();
            } catch (Exception e) {
                log.error("[CatchUp] Failed to fetch historical data for userId={} symbols={}. Aborting.", userId, symbolsParam, e);
                return;
            }

            if (histResponse == null || histResponse.getData() == null || histResponse.getData().isEmpty()) {
                log.warn("[CatchUp] Market Data API returned empty response for userId={} symbols={} wrapperNull={} dataNull={}",
                        userId, symbolsParam, histResponse == null,
                        histResponse == null || histResponse.getData() == null);
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
                if (symbolData == null) {
                    continue;
                }
                // Prefer flat am-market dataPoints; nested legacy envelope still works.
                java.util.List<com.am.common.investment.model.historical.OHLCVTPoint> points =
                        symbolData.effectiveDataPoints();
                if (points == null || points.isEmpty()) {
                    continue;
                }
                // Bare ticker key for holding lookup (NSE_EQ:PWL → PWL).
                String bare = symbol.contains(":")
                        ? symbol.substring(symbol.lastIndexOf(':') + 1)
                        : symbol;
                for (com.am.common.investment.model.historical.OHLCVTPoint point : points) {
                    if (point == null || point.getClose() == null || point.getTime() == null) {
                        continue;
                    }
                    LocalDate date = point.getTime().toLocalDate();
                    Map<String, Double> dayMap = priceLookup.computeIfAbsent(date, k -> new HashMap<>());
                    dayMap.put(symbol, point.getClose());
                    dayMap.put(bare, point.getClose());
                    if (bare != null) {
                        dayMap.put(bare.toUpperCase(java.util.Locale.ROOT), point.getClose());
                    }
                }
            }

            log.info("[CatchUp] Built price lookup map for {} trading days for userId={}.", priceLookup.size(), userId);

            // --- STEP 5: Load existing snapshots in bulk (idempotency + freeze repair) ---
            Map<LocalDate, PortfolioSnapshotDocument> existingByDate = portfolioSnapshotRepository
                    .findByUserIdAndSnapshotDateBetween(userId, backfillStart, today.minusDays(1))
                    .stream()
                    .filter(d -> d.getSnapshotDate() != null)
                    .collect(Collectors.toMap(PortfolioSnapshotDocument::getSnapshotDate, d -> d, (a, b) -> a));

            // --- STEP 6: Loop through each missing / repairable day and reconstruct snapshots ---
            List<PortfolioSnapshotDocument> batch = new ArrayList<>();

            // Track "last known price" per symbol to carry-forward over weekends/holidays
            Map<String, Double> lastKnownPrice = new HashMap<>();
            // Prior portfolio close → next day's session open (weekend day-P&L freeze).
            Map<String, Double> lastPortClose = new HashMap<>();

            for (LocalDate date = backfillStart; date.isBefore(today); date = date.plusDays(1)) {
                // Update last known price for each symbol if today is a trading day
                Map<String, Double> dayPrices = priceLookup.getOrDefault(date, Collections.emptyMap());
                lastKnownPrice.putAll(dayPrices);

                PortfolioSnapshotDocument existingDoc = existingByDate.get(date);
                if (existingDoc != null && !needsSessionDayFreezeRepair(existingDoc)) {
                    // Seed prior closes so the next day can freeze correctly.
                    seedLastPortClose(lastPortClose, existingDoc);
                    log.debug("[CatchUp] Snapshot already exists for date={}, userId={}. Skipping.", date, userId);
                    continue;
                }

                // If we have no price data at all yet (e.g. API has no data for this range),
                // use the last snapshot's close as the final fallback.
                // This ensures even weekends before any trading data still produce valid snapshots.
                if (lastKnownPrice.isEmpty()) {
                    if (existingDoc != null) {
                        seedLastPortClose(lastPortClose, existingDoc);
                    }
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

                    double sessionOpen = lastPortClose.getOrDefault(portfolioId, portValue);
                    Double sessionDayGain = null;
                    Double sessionDayPct = null;
                    // Only freeze when we had a real prior close (not first-day open=close fallback).
                    boolean hadPrior = lastPortClose.containsKey(portfolioId);
                    if (hadPrior && sessionOpen > 0) {
                        double rawDay = portValue - sessionOpen;
                        if (Math.abs(rawDay) >= 0.005) {
                            sessionDayGain = rawDay;
                            sessionDayPct = (rawDay / sessionOpen) * 100.0;
                        }
                    }
                    entries.add(PortfolioSnapshotEntry.builder()
                            .portfolioId(portfolioId)
                            .portfolioName(portfolioName)
                            .brokerType(brokerStr)
                            .open(sessionOpen)
                            .high(Math.max(sessionOpen, portValue))
                            .low(Math.min(sessionOpen, portValue))
                            .close(portValue)
                            .totalInvestment(portInvestment)
                            .totalGainLoss(portGainLoss)
                            .totalGainLossPercentage(portGainLossPct)
                            .sessionDayGainLoss(sessionDayGain)
                            .sessionDayGainLossPercentage(sessionDayPct)
                            .holdings(catchUpHoldings)
                            .build());
                    lastPortClose.put(portfolioId, portValue);

                    totalWealth += portValue;
                    totalInvestment += portInvestment;
                }

                if (entries.isEmpty()) {
                    continue;
                }

                double totalGainLoss = totalWealth - totalInvestment;
                double totalGainLossPct = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100.0 : 0.0;
                Double userSessionDay = null;
                Double userSessionDayPct = null;
                double sessionDaySum = 0.0;
                double sessionOpenSum = 0.0;
                int sessionDayN = 0;
                for (PortfolioSnapshotEntry e : entries) {
                    if (e.getSessionDayGainLoss() == null) {
                        continue;
                    }
                    sessionDaySum += e.getSessionDayGainLoss();
                    if (e.getOpen() != null && e.getOpen() > 0) {
                        sessionOpenSum += e.getOpen();
                    }
                    sessionDayN++;
                }
                if (sessionDayN > 0) {
                    userSessionDay = sessionDaySum;
                    userSessionDayPct = sessionOpenSum > 0 ? (sessionDaySum / sessionOpenSum) * 100.0 : null;
                }

                String snapshotId = existingDoc != null && existingDoc.getId() != null
                        ? existingDoc.getId()
                        : UUID.randomUUID().toString();
                String publicSnapshotId = existingDoc != null && existingDoc.getSnapshotId() != null
                        ? existingDoc.getSnapshotId()
                        : snapshotId;
                batch.add(PortfolioSnapshotDocument.builder()
                        .id(snapshotId)
                        .snapshotId(publicSnapshotId)
                        .userId(userId)
                        .snapshotDate(date)
                        .totalUserWealth(totalWealth)
                        .totalUserInvestment(totalInvestment)
                        .totalUserGainLoss(totalGainLoss)
                        .totalUserGainLossPercentage(totalGainLossPct)
                        .sessionDayGainLoss(userSessionDay)
                        .sessionDayGainLossPercentage(userSessionDayPct)
                        .portfolios(entries)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            // --- STEP 7: Bulk upsert reconstructed / freeze-repaired snapshots ---
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

    /** True when a snap lacks usable sessionDayGainLoss (legacy open=close / null freeze). */
    static boolean needsSessionDayFreezeRepair(PortfolioSnapshotDocument doc) {
        if (doc == null || doc.getPortfolios() == null || doc.getPortfolios().isEmpty()) {
            return doc == null || doc.getSessionDayGainLoss() == null
                    || Math.abs(doc.getSessionDayGainLoss()) < 0.005;
        }
        for (PortfolioSnapshotEntry e : doc.getPortfolios()) {
            if (e == null) {
                continue;
            }
            Double day = e.getSessionDayGainLoss();
            if (day != null && Math.abs(day) >= 0.005) {
                continue;
            }
            Double open = e.getOpen();
            Double close = e.getClose();
            if (open != null && close != null && Math.abs(open - close) < 0.005) {
                return true;
            }
            if (day == null) {
                return true;
            }
        }
        return false;
    }

    private static void seedLastPortClose(Map<String, Double> lastPortClose, PortfolioSnapshotDocument doc) {
        if (doc == null || doc.getPortfolios() == null) {
            return;
        }
        for (PortfolioSnapshotEntry e : doc.getPortfolios()) {
            if (e == null || e.getPortfolioId() == null || e.getClose() == null || e.getClose() <= 0) {
                continue;
            }
            lastPortClose.put(e.getPortfolioId(), e.getClose());
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
