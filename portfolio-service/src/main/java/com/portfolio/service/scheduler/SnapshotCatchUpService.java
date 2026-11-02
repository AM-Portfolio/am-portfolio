package com.portfolio.service.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.resolver.TradingSymbolResolver;
import com.portfolio.service.resolver.PortfolioEquitySymbolNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconstructs historical portfolio snapshots from market daily closes.
 * Supports FULL / MERGE_BROKER / GAP modes with entry–exit window clipping (max 365d).
 * Chart wealth is mark-to-market (qty × historical close); avgBuy may be 0 for email books.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotCatchUpService {

    @Value("${app.scheduler.snapshot.max-backfill-days:730}")
    private int maxBackfillDays;

    @Value("${app.history.job.pass-a-days:90}")
    private int passADays;

    /**
     * One symbol per request: a single hanging ticker (e.g. TMCV on dig) blocks the whole
     * multi-symbol market historical call past the WebClient timeout.
     */
    @Value("${app.history.fetch.chunk-size:1}")
    private int fetchChunkSize;

    /** Dig market CB opens under parallel hist — keep serial by default. */
    @Value("${app.history.fetch.max-parallel:1}")
    private int fetchMaxParallel;

    /** Per-call mono timeout for history catch-up (ms). Below WebClient 45s so hangers fail fast. */
    @Value("${app.history.fetch.per-call-timeout-ms:12000}")
    private long fetchPerCallTimeoutMs;

    /** Refuse READY when fewer than this fraction of loadable tickers have any historical close. */
    @Value("${app.history.coverage.min-priced-fraction:0.8}")
    private double minPricedFraction;

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioService portfolioService;
    private final MarketDataApiClient marketDataApiClient;
    private final PortfolioEquitySymbolNormalizer equitySymbolNormalizer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CacheManager cacheManager;

    public record CatchUpResult(
            LocalDate historyFrom,
            LocalDate historyTo,
            int daysWritten,
            int holdingsLoaded,
            int symbolsSkippedIsin,
            int symbolsPriced,
            int symbolsUnpricedSkipped) {
        public CatchUpResult(LocalDate historyFrom, LocalDate historyTo, int daysWritten) {
            this(historyFrom, historyTo, daysWritten, 0, 0, 0, 0);
        }
    }

    /** Mutable counters for one history build (instrumentation + READY gate). */
    static final class CoverageStats {
        int holdingsLoaded;
        int symbolsSkippedIsin;
        int loadableTickers;
        int symbolsPriced;
        int symbolsUnpricedSkipped;
        double lastDayWealth;
        int lastDayHoldingsPriced;
    }

    /**
     * Login / legacy async entry — GAP fill only.
     */
    @Async("taskExecutor")
    public void triggerCatchUp(String userId) {
        log.info("[CatchUp] Legacy triggerCatchUp userId={} (GAP)", userId);
        try {
            runHistoryBuild(userId, HistoryJobMode.GAP, null, phase -> {});
        } catch (Exception e) {
            log.error("[CatchUp] Unexpected failure userId={}", userId, e);
        }
    }

    /**
     * Synchronous build used by {@link PortfolioHistoryJobService}.
     */
    public CatchUpResult runHistoryBuild(
            String userId,
            HistoryJobMode mode,
            String targetPortfolioId,
            Consumer<HistoryJobPhase> phaseCallback) {

        LocalDate today = LocalDate.now();
        LocalDate hardCap = today.minusDays(maxBackfillDays);
        LocalDate yesterday = today.minusDays(1);

        CoverageStats coverage = new CoverageStats();
        Map<String, List<HoldingInfo>> portfolioHoldings =
                loadCurrentHoldings(userId, targetPortfolioId, mode, coverage);
        if (portfolioHoldings.isEmpty()) {
            log.warn("[HistoryJob] No holdings for userId={} mode={} target={} holdingsLoaded={} skippedIsin={} — abort",
                    userId, mode, targetPortfolioId, coverage.holdingsLoaded, coverage.symbolsSkippedIsin);
            throw new IllegalStateException("No valid equity holdings for history build");
        }

        Window window = resolveWindow(portfolioHoldings, mode, userId, hardCap, yesterday);
        if (window.from().isAfter(window.to())) {
            log.info("[HistoryJob] Empty window userId={} — nothing to build", userId);
            return new CatchUpResult(null, null, 0,
                    coverage.holdingsLoaded, coverage.symbolsSkippedIsin, 0, 0);
        }

        log.info("[HistoryJob] Window resolved userId={} mode={} from={} to={} loadableTickers={} holdingsLoaded={} skippedIsin={} portfolios={}",
                userId, mode, window.from(), window.to(),
                coverage.loadableTickers,
                coverage.holdingsLoaded,
                coverage.symbolsSkippedIsin,
                portfolioHoldings.size());

        Set<String> allSymbols = new HashSet<>();
        portfolioHoldings.values().forEach(list -> list.forEach(h -> allSymbols.add(h.symbol)));

        Map<LocalDate, Map<String, Double>> priceLookup = fetchPrices(userId, allSymbols, window.from(), window.to());
        retryUnpriced(userId, allSymbols, window.from(), window.to(), priceLookup);
        applyPriceCoverage(allSymbols, priceLookup, coverage);
        assertPriceCoverage(userId, coverage);

        LocalDate passAEnd = window.from().plusDays(Math.min(passADays, maxBackfillDays) - 1L);
        if (passAEnd.isAfter(window.to())) {
            passAEnd = window.to();
        }

        phaseCallback.accept(HistoryJobPhase.BUILDING_90D);
        int written = writeRange(userId, mode, targetPortfolioId, portfolioHoldings, priceLookup,
                window.from(), passAEnd, mode == HistoryJobMode.GAP, coverage);

        if (passAEnd.isBefore(window.to())) {
            phaseCallback.accept(HistoryJobPhase.BUILDING_1Y);
            written += writeRange(userId, mode, targetPortfolioId, portfolioHoldings, priceLookup,
                    passAEnd.plusDays(1), window.to(), mode == HistoryJobMode.GAP, coverage);
        }

        log.info("[HistoryJob] Build complete userId={} daysWritten={} from={} to={} holdingsLoaded={} skippedIsin={} priced={} unpricedSkipped={} lastDayWealth={} lastDayHoldingsPriced={}",
                userId, written, window.from(), window.to(),
                coverage.holdingsLoaded, coverage.symbolsSkippedIsin,
                coverage.symbolsPriced, coverage.symbolsUnpricedSkipped,
                coverage.lastDayWealth, coverage.lastDayHoldingsPriced);
        return new CatchUpResult(
                window.from(),
                window.to(),
                written,
                coverage.holdingsLoaded,
                coverage.symbolsSkippedIsin,
                coverage.symbolsPriced,
                coverage.symbolsUnpricedSkipped);
    }

    private Map<String, List<HoldingInfo>> loadCurrentHoldings(
            String userId, String targetPortfolioId, HistoryJobMode mode, CoverageStats coverage) {
        Map<String, List<HoldingInfo>> portfolioHoldings = new HashMap<>();
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            return portfolioHoldings;
        }
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
                continue;
            }
            if (portfolio.getPortfolioKind() != null && !PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
                continue;
            }
            String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : null;
            if (portfolioId == null) {
                continue;
            }
            // FULL and MERGE_BROKER always load every broker so All / totals stay coherent.
            // MERGE still upserts only the target nested entry in writeRange; FULL replaces all.
            if (targetPortfolioId != null
                    && mode == HistoryJobMode.GAP
                    && !targetPortfolioId.equals(portfolioId)) {
                continue;
            }
            // Same path as live summary: ISIN / broker alias → NSE ticker before history fetch.
            if (equitySymbolNormalizer != null) {
                equitySymbolNormalizer.normalizeEquities(portfolio.getEquityModels());
            }
            String brokerStr = portfolio.getBrokerType() != null ? portfolio.getBrokerType().name() : null;
            List<HoldingInfo> holdingInfos = new ArrayList<>();
            for (EquityModel e : portfolio.getEquityModels()) {
                if (e.getQuantity() == null || e.getQuantity() <= 0) {
                    continue;
                }
                coverage.holdingsLoaded++;
                String marketSymbol = resolveMarketSymbol(e);
                if (marketSymbol == null || marketSymbol.isBlank()) {
                    coverage.symbolsSkippedIsin++;
                    log.warn("[HistoryJob] Skip unresolved symbol portfolioId={} rawSymbol={} isin={} name={}",
                            portfolioId, e.getSymbol(), e.getIsin(), e.getName());
                    continue;
                }
                LocalDate entry = resolveEntry(e);
                LocalDate exit = resolveExit(e);
                holdingInfos.add(new HoldingInfo(
                        marketSymbol,
                        e.getQuantity(),
                        e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice() : 0.0,
                        brokerStr,
                        portfolio.getName(),
                        entry,
                        exit));
            }
            if (!holdingInfos.isEmpty()) {
                portfolioHoldings.put(portfolioId, holdingInfos);
            }
        }
        coverage.loadableTickers = (int) portfolioHoldings.values().stream()
                .flatMap(List::stream)
                .map(h -> h.symbol)
                .distinct()
                .count();
        return portfolioHoldings;
    }

    private LocalDate resolveEntry(EquityModel e) {
        if (e.getCreatedAt() != null) {
            return e.getCreatedAt().toLocalDate();
        }
        return null;
    }

    private LocalDate resolveExit(EquityModel e) {
        String status = e.getStatus();
        if (status != null && status.equalsIgnoreCase("CLOSED") && e.getUpdatedAt() != null) {
            return e.getUpdatedAt().toLocalDate();
        }
        return null;
    }

    /**
     * Prefer compact tickers after {@link PortfolioEquitySymbolNormalizer} has run.
     * Still-ISIN-shaped ids are skipped — historical-data batches fail on unresolved MF ISINs.
     */
    private String resolveMarketSymbol(EquityModel e) {
        String symbol = e.getSymbol();
        if (TradingSymbolResolver.looksLikeTradingTicker(symbol)) {
            return symbol.trim().toUpperCase();
        }
        // Prefer already-resolved ticker fields over raw ISIN for history pricing.
        if (e.getIsin() != null && !e.getIsin().isBlank()
                && TradingSymbolResolver.looksLikeTradingTicker(e.getIsin())) {
            return e.getIsin().trim().toUpperCase();
        }
        if (TradingSymbolResolver.looksLikeIsin(symbol)) {
            return null;
        }
        if (e.getIsin() != null && TradingSymbolResolver.looksLikeIsin(e.getIsin())) {
            return null;
        }
        return symbol != null ? symbol.trim().toUpperCase() : null;
    }

    private void retryUnpriced(
            String userId,
            Set<String> allSymbols,
            LocalDate from,
            LocalDate to,
            Map<LocalDate, Map<String, Double>> priceLookup) {
        Set<String> priced = collectPricedSymbols(priceLookup);
        Set<String> unpriced = new HashSet<>();
        for (String s : allSymbols) {
            if (s != null && !priced.contains(s.trim().toUpperCase())) {
                unpriced.add(s.trim().toUpperCase());
            }
        }
        if (unpriced.isEmpty()) {
            return;
        }
        log.warn("[HistoryJob] Retrying unpriced tickers userId={} count={} sample={}",
                userId, unpriced.size(), unpriced.stream().limit(8).collect(Collectors.toList()));
        Map<LocalDate, Map<String, Double>> retry = fetchPrices(userId, unpriced, from, to);
        for (Map.Entry<LocalDate, Map<String, Double>> e : retry.entrySet()) {
            priceLookup.computeIfAbsent(e.getKey(), k -> new HashMap<>()).putAll(e.getValue());
        }
    }

    static Set<String> collectPricedSymbols(Map<LocalDate, Map<String, Double>> priceLookup) {
        Set<String> priced = new HashSet<>();
        if (priceLookup == null) {
            return priced;
        }
        for (Map<String, Double> day : priceLookup.values()) {
            if (day == null) {
                continue;
            }
            for (String sym : day.keySet()) {
                String n = normalizePriceSymbol(sym);
                if (n != null) {
                    priced.add(n);
                }
            }
        }
        return priced;
    }

    private void applyPriceCoverage(
            Set<String> allSymbols,
            Map<LocalDate, Map<String, Double>> priceLookup,
            CoverageStats coverage) {
        Set<String> priced = collectPricedSymbols(priceLookup);
        int pricedCount = 0;
        for (String s : allSymbols) {
            if (s != null && priced.contains(s.trim().toUpperCase())) {
                pricedCount++;
            }
        }
        coverage.symbolsPriced = pricedCount;
        coverage.symbolsUnpricedSkipped = Math.max(0, allSymbols.size() - pricedCount);
    }

    void assertPriceCoverage(String userId, CoverageStats coverage) {
        if (coverage.loadableTickers <= 0) {
            return;
        }
        double fraction = (double) coverage.symbolsPriced / (double) coverage.loadableTickers;
        double min = minPricedFraction > 0 && minPricedFraction <= 1.0 ? minPricedFraction : 0.8;
        log.info("[HistoryJob] Price coverage userId={} holdingsLoaded={} skippedIsin={} loadable={} priced={} unpricedSkipped={} fraction={}",
                userId,
                coverage.holdingsLoaded,
                coverage.symbolsSkippedIsin,
                coverage.loadableTickers,
                coverage.symbolsPriced,
                coverage.symbolsUnpricedSkipped,
                String.format("%.3f", fraction));
        if (fraction < min) {
            throw new IllegalStateException(String.format(
                    "History price coverage too low: priced=%d loadable=%d fraction=%.3f min=%.3f holdingsLoaded=%d skippedIsin=%d",
                    coverage.symbolsPriced,
                    coverage.loadableTickers,
                    fraction,
                    min,
                    coverage.holdingsLoaded,
                    coverage.symbolsSkippedIsin));
        }
    }

    private record Window(LocalDate from, LocalDate to) {}

    private Window resolveWindow(
            Map<String, List<HoldingInfo>> holdings,
            HistoryJobMode mode,
            String userId,
            LocalDate hardCap,
            LocalDate yesterday) {

        LocalDate lifeStart = null;
        LocalDate lifeEnd = yesterday;
        for (List<HoldingInfo> list : holdings.values()) {
            for (HoldingInfo h : list) {
                if (h.entryDate != null) {
                    lifeStart = lifeStart == null || h.entryDate.isBefore(lifeStart) ? h.entryDate : lifeStart;
                }
                if (h.exitDate != null) {
                    // open positions keep lifeEnd at yesterday; closed may pull lifeEnd earlier only if ALL closed
                }
            }
        }

        if (mode == HistoryJobMode.GAP) {
            List<PortfolioSnapshotDocument> latest = portfolioSnapshotRepository
                    .findByUserIdOrderBySnapshotDateDesc(userId, PageRequest.of(0, 1));
            if (latest.isEmpty()) {
                LocalDate from = lifeStart != null ? lifeStart : yesterday.minusDays(Math.min(90, maxBackfillDays) - 1L);
                if (from.isBefore(hardCap)) {
                    from = hardCap;
                }
                if (from.isAfter(yesterday)) {
                    from = yesterday;
                }
                return new Window(from, yesterday);
            }
            LocalDate last = latest.get(0).getSnapshotDate();
            if (!last.isBefore(yesterday)) {
                return new Window(yesterday.plusDays(1), yesterday); // empty
            }
            LocalDate from = last.plusDays(1);
            if (from.isBefore(hardCap)) {
                from = hardCap;
            }
            return new Window(from, yesterday);
        }

        // FULL / MERGE: lifespan ∩ hardCap..yesterday
        LocalDate from = lifeStart != null ? lifeStart : hardCap;
        if (from.isBefore(hardCap)) {
            from = hardCap;
        }
        if (from.isAfter(yesterday)) {
            from = yesterday;
        }
        return new Window(from, yesterday);
    }

    private Map<LocalDate, Map<String, Double>> fetchPrices(
            String userId, Set<String> symbols, LocalDate from, LocalDate to) {
        Map<LocalDate, Map<String, Double>> priceLookup = new HashMap<>();
        List<String> symbolList = symbols.stream()
                .filter(TradingSymbolResolver::looksLikeTradingTicker)
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .collect(Collectors.toList());
        if (symbolList.isEmpty()) {
            log.warn("[HistoryJob] No trading tickers to price userId={} rawSymbols={}", userId, symbols.size());
            return priceLookup;
        }
        int chunkSize = fetchChunkSize > 0 ? fetchChunkSize : 1;
        long t0 = System.currentTimeMillis();
        int ok = 0;
        int failed = 0;

        // Serial per-symbol (or tiny chunks): never amplify one hanger across a batch.
        for (int i = 0; i < symbolList.size(); i += chunkSize) {
            List<String> chunk = new ArrayList<>(symbolList.subList(i, Math.min(i + chunkSize, symbolList.size())));
            if (fetchPriceChunk(userId, chunk, from, to, priceLookup)) {
                ok += chunk.size();
            } else if (chunk.size() > 1) {
                for (String one : chunk) {
                    if (fetchPriceChunk(userId, List.of(one), from, to, priceLookup)) {
                        ok++;
                    } else {
                        failed++;
                    }
                }
            } else {
                failed++;
            }
        }

        log.info("[HistoryJob] Price map days={} userId={} tickers={} ok={} failed={} chunkSize={} elapsedMs={}",
                priceLookup.size(), userId, symbolList.size(), ok, failed, chunkSize,
                System.currentTimeMillis() - t0);
        return priceLookup;
    }

    /** @return true when the response had at least one symbol series */
    private boolean fetchPriceChunk(
            String userId,
            List<String> chunk,
            LocalDate from,
            LocalDate to,
            Map<LocalDate, Map<String, Double>> priceLookup) {
        String symbolsParam = String.join(",", chunk);
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(symbolsParam)
                .fromDate(from.toString())
                .toDate(to.toString())
                .interval("day")
                .forceRefresh(false)
                .build();
        log.info("[HistoryJob] Market historical fetch userId={} symbols={} from={} to={}",
                userId, chunk.size(), from, to);
        HistoricalDataResponseWrapper histResponse = null;
        final int maxAttempts = 2;
        long callTimeoutMs = fetchPerCallTimeoutMs > 0 ? fetchPerCallTimeoutMs : 12_000L;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                histResponse = marketDataApiClient.getHistoricalData(request)
                        .timeout(java.time.Duration.ofMillis(callTimeoutMs))
                        .block();
                break;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                boolean cbBlocked = msg.contains("CallNotPermitted")
                        || msg.contains("CircuitBreaker")
                        || (e.getCause() != null && String.valueOf(e.getCause()).contains("CallNotPermitted"));
                if (attempt < maxAttempts && cbBlocked) {
                    long sleepMs = 5_000L * attempt;
                    log.warn("[HistoryJob] Market fetch retry userId={} attempt={}/{} sleepMs={} reason={}",
                            userId, attempt, maxAttempts, sleepMs, msg);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }
                // Timeouts / hangers: skip symbol, do not burn 45s×retries.
                log.warn("[HistoryJob] Market fetch skip userId={} symbols={} reason={}",
                        userId, symbolsParam, msg);
                return false;
            }
        }
        if (histResponse == null) {
            return false;
        }
        if (histResponse == null || histResponse.getData() == null || histResponse.getData().isEmpty()) {
            log.warn("[HistoryJob] Empty market response for chunk userId={} error={} symbols={}",
                    userId,
                    histResponse != null ? histResponse.getError() : "null-response",
                    symbolsParam);
            return false;
        }
        for (Map.Entry<String, com.portfolio.marketdata.model.HistoricalDataResponse> entry
                : histResponse.getData().entrySet()) {
            String symbol = normalizePriceSymbol(entry.getKey());
            var response = entry.getValue();
            if (symbol == null || response == null) {
                continue;
            }
            for (var point : response.effectiveDataPoints()) {
                if (point == null || point.getClose() == null || point.getTime() == null) {
                    continue;
                }
                LocalDate date = point.getTime().toLocalDate();
                priceLookup.computeIfAbsent(date, k -> new HashMap<>()).put(symbol, point.getClose());
            }
        }
        return true;
    }

    private int writeRange(
            String userId,
            HistoryJobMode mode,
            String targetPortfolioId,
            Map<String, List<HoldingInfo>> portfolioHoldings,
            Map<LocalDate, Map<String, Double>> priceLookup,
            LocalDate from,
            LocalDate to,
            boolean skipExisting,
            CoverageStats coverage) {

        Set<LocalDate> existingDates = skipExisting
                ? portfolioSnapshotRepository.findByUserIdAndSnapshotDateBetween(userId, from, to).stream()
                        .map(PortfolioSnapshotDocument::getSnapshotDate)
                        .collect(Collectors.toSet())
                : Collections.emptySet();

        Map<String, Double> lastKnownPrice = new HashMap<>();
        int written = 0;
        List<PortfolioSnapshotDocument> batch = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (skipExisting && existingDates.contains(date)) {
                log.debug("[HistoryJob] Skip existing date={} userId={}", date, userId);
                continue;
            }

            Map<String, Double> dayPrices = priceLookup.getOrDefault(date, Collections.emptyMap());
            lastKnownPrice.putAll(dayPrices);
            if (lastKnownPrice.isEmpty()) {
                continue;
            }

            DayBuildResult dayBuild = buildEntriesForDateWithStats(portfolioHoldings, lastKnownPrice, date);
            List<PortfolioSnapshotEntry> newEntries = dayBuild.entries();
            if (newEntries.isEmpty()) {
                continue;
            }

            Optional<PortfolioSnapshotDocument> existingOpt =
                    portfolioSnapshotRepository.findByUserIdAndSnapshotDate(userId, date);

            PortfolioSnapshotDocument doc;
            if (existingOpt.isPresent() && mode == HistoryJobMode.FULL) {
                // Full rebuild: replace all nested brokers for the day (no stale leftover ids).
                doc = existingOpt.get();
                doc.setPortfolios(newEntries);
                recomputeTotals(doc);
                doc.setCreatedAt(LocalDateTime.now());
            } else if (existingOpt.isPresent() && mode == HistoryJobMode.MERGE_BROKER) {
                doc = existingOpt.get();
                List<PortfolioSnapshotEntry> merged = mergeEntries(doc.getPortfolios(), newEntries, targetPortfolioId);
                doc.setPortfolios(merged);
                recomputeTotals(doc);
                doc.setCreatedAt(LocalDateTime.now());
            } else if (existingOpt.isPresent() && mode == HistoryJobMode.GAP) {
                continue;
            } else {
                List<PortfolioSnapshotEntry> toWrite = newEntries;
                // MERGE on a brand-new day must not insert sibling brokers (only the target book).
                if (mode == HistoryJobMode.MERGE_BROKER && targetPortfolioId != null) {
                    toWrite = newEntries.stream()
                            .filter(e -> targetPortfolioId.equals(e.getPortfolioId()))
                            .collect(Collectors.toList());
                    if (toWrite.isEmpty()) {
                        continue;
                    }
                }
                String snapshotId = UUID.randomUUID().toString();
                double totalWealth = 0;
                double totalInvestment = 0;
                for (PortfolioSnapshotEntry e : toWrite) {
                    totalWealth += e.getClose() != null ? e.getClose() : 0;
                    totalInvestment += e.getTotalInvestment() != null ? e.getTotalInvestment() : 0;
                }
                double gl = totalWealth - totalInvestment;
                double glPct = totalInvestment > 0 ? (gl / totalInvestment) * 100.0 : 0.0;
                doc = PortfolioSnapshotDocument.builder()
                        .id(snapshotId)
                        .snapshotId(snapshotId)
                        .userId(userId)
                        .snapshotDate(date)
                        .totalUserWealth(totalWealth)
                        .totalUserInvestment(totalInvestment)
                        .totalUserGainLoss(gl)
                        .totalUserGainLossPercentage(glPct)
                        .portfolios(toWrite)
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            if (coverage != null) {
                coverage.lastDayWealth = doc.getTotalUserWealth() != null ? doc.getTotalUserWealth() : 0;
                coverage.lastDayHoldingsPriced = dayBuild.holdingsPriced();
            }
            if (written == 0 || date.equals(to)) {
                log.info("[HistoryJob] Day snapshot userId={} date={} wealth={} holdingsPriced={} unpricedActive={} portfolios={}",
                        userId, date, doc.getTotalUserWealth(), dayBuild.holdingsPriced(),
                        dayBuild.holdingsUnpriced(), newEntries.size());
            }
            batch.add(doc);
            written++;
            if (batch.size() >= 50) {
                portfolioSnapshotRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            portfolioSnapshotRepository.saveAll(batch);
        }
        if (written > 0) {
            evictHistoryCache();
        }
        return written;
    }

    private void evictHistoryCache() {
        if (cacheManager == null) {
            return;
        }
        try {
            var cache = cacheManager.getCache("portfolioHistory");
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("[HistoryJob] Failed to clear portfolioHistory cache: {}", e.getMessage());
        }
    }

    private List<PortfolioSnapshotEntry> mergeEntries(
            List<PortfolioSnapshotEntry> existing,
            List<PortfolioSnapshotEntry> incoming,
            String targetPortfolioId) {
        Map<String, PortfolioSnapshotEntry> byId = new HashMap<>();
        if (existing != null) {
            for (PortfolioSnapshotEntry e : existing) {
                if (e.getPortfolioId() != null) {
                    byId.put(e.getPortfolioId(), e);
                }
            }
        }
        for (PortfolioSnapshotEntry e : incoming) {
            if (targetPortfolioId == null || targetPortfolioId.equals(e.getPortfolioId())) {
                byId.put(e.getPortfolioId(), e);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void recomputeTotals(PortfolioSnapshotDocument doc) {
        double wealth = 0;
        double inv = 0;
        if (doc.getPortfolios() != null) {
            for (PortfolioSnapshotEntry e : doc.getPortfolios()) {
                wealth += e.getClose() != null ? e.getClose() : 0;
                inv += e.getTotalInvestment() != null ? e.getTotalInvestment() : 0;
            }
        }
        double gl = wealth - inv;
        doc.setTotalUserWealth(wealth);
        doc.setTotalUserInvestment(inv);
        doc.setTotalUserGainLoss(gl);
        doc.setTotalUserGainLossPercentage(inv > 0 ? (gl / inv) * 100.0 : 0.0);
    }

    /**
     * Resolve mark-to-market price for a holding.
     * Never fall back to avgBuy ≤ 0 — that collapses early wealth and inflates Overall %.
     *
     * @return price to use, or empty if the holding should be skipped for this day
     */
    /** Market responses often key as {@code NSE_EQ:TCS}; holdings use bare {@code TCS}. */
    static String normalizePriceSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase();
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && colon < s.length() - 1) {
            s = s.substring(colon + 1);
        }
        return s.isBlank() ? null : s;
    }

    static Optional<Double> resolveHoldingPrice(Double marketClose, double avgBuyPrice) {
        if (marketClose != null && marketClose > 0 && Double.isFinite(marketClose)) {
            return Optional.of(marketClose);
        }
        if (avgBuyPrice > 0 && Double.isFinite(avgBuyPrice)) {
            return Optional.of(avgBuyPrice);
        }
        return Optional.empty();
    }

    record DayBuildResult(List<PortfolioSnapshotEntry> entries, int holdingsPriced, int holdingsUnpriced) {}

    List<PortfolioSnapshotEntry> buildEntriesForDate(
            Map<String, List<HoldingInfo>> portfolioHoldings,
            Map<String, Double> lastKnownPrice,
            LocalDate date) {
        return buildEntriesForDateWithStats(portfolioHoldings, lastKnownPrice, date).entries();
    }

    DayBuildResult buildEntriesForDateWithStats(
            Map<String, List<HoldingInfo>> portfolioHoldings,
            Map<String, Double> lastKnownPrice,
            LocalDate date) {
        List<PortfolioSnapshotEntry> entries = new ArrayList<>();
        int holdingsPriced = 0;
        int holdingsUnpriced = 0;
        for (Map.Entry<String, List<HoldingInfo>> portEntry : portfolioHoldings.entrySet()) {
            String portfolioId = portEntry.getKey();
            List<HoldingInfo> holdings = portEntry.getValue();

            double portValue = 0.0;
            double portInvestment = 0.0;
            List<HoldingSnapshotItem> snapHoldings = new ArrayList<>();

            for (HoldingInfo h : holdings) {
                if (!h.isActiveOn(date)) {
                    continue;
                }
                Optional<Double> priceOpt = resolveHoldingPrice(lastKnownPrice.get(h.symbol), h.avgBuyPrice);
                if (priceOpt.isEmpty()) {
                    holdingsUnpriced++;
                    continue;
                }
                double price = priceOpt.get();
                holdingsPriced++;
                portValue += h.quantity * price;
                portInvestment += h.quantity * h.avgBuyPrice;
                snapHoldings.add(HoldingSnapshotItem.builder()
                        .symbol(h.symbol)
                        .quantity(h.quantity)
                        .avgBuyPrice(h.avgBuyPrice)
                        .build());
            }
            if (snapHoldings.isEmpty()) {
                continue;
            }
            double portGainLoss = portValue - portInvestment;
            double portGainLossPct = portInvestment > 0 ? (portGainLoss / portInvestment) * 100.0 : 0.0;
            String brokerStr = holdings.isEmpty() ? null : holdings.get(0).brokerType;
            String portfolioName = holdings.isEmpty() ? null : holdings.get(0).portfolioName;
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
                    .holdings(snapHoldings)
                    .build());
        }
        return new DayBuildResult(entries, holdingsPriced, holdingsUnpriced);
    }

    static final class HoldingInfo {
        final String symbol;
        final double quantity;
        final double avgBuyPrice;
        final String brokerType;
        final String portfolioName;
        final LocalDate entryDate;
        final LocalDate exitDate;

        HoldingInfo(String symbol, double quantity, double avgBuyPrice, String brokerType,
                String portfolioName, LocalDate entryDate, LocalDate exitDate) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.avgBuyPrice = avgBuyPrice;
            this.brokerType = brokerType;
            this.portfolioName = portfolioName;
            this.entryDate = entryDate;
            this.exitDate = exitDate;
        }

        boolean isActiveOn(LocalDate date) {
            if (entryDate != null && date.isBefore(entryDate)) {
                return false;
            }
            if (exitDate != null && date.isAfter(exitDate)) {
                return false;
            }
            return true;
        }
    }
}
