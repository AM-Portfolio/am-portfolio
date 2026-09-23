package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.intelligence.CachedIntelligenceHistory;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.model.util.SymbolResolver;
import com.portfolio.redis.service.PortfolioIntelligenceHistoryRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Builds {@link PortfolioIntelligenceSnapshot} from portfolio holdings + live prices + optional history.
 * History/vol/beta/performance omitted when series unavailable or timed out ({@code historyPoints < 20}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioIntelligenceSnapshotFactory {

    /** Default primary benchmark; override via portfolio.intelligence.primary-benchmark-symbol (e.g. SENSEX). */
    public static final String NIFTY_SYMBOL = "NIFTY 50";
    public static final int HISTORY_LOOKBACK_DAYS = 60;
    public static final long HISTORY_TIMEOUT_MS = 800L;
    public static final long HISTORY_TIMEOUT_STRESS_MS = 20_000L;

    @Value("${portfolio.intelligence.primary-benchmark-symbol:NIFTY 50}")
    private String primaryBenchmarkSymbol;

    @Value("${portfolio.intelligence.history-lookback-days:60}")
    private int historyLookbackDays;

    @Value("${portfolio.intelligence.history-timeout-ms:800}")
    private long historyTimeoutMs;

    @Value("${portfolio.intelligence.history-timeout-stress-ms:20000}")
    private long historyTimeoutStressMs;

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final SecurityDetailsService securityDetailsService;
    private final PortfolioIntelligenceHistoryRedisService historyCache;

    private final ConcurrentHashMap<String, CompletableFuture<HistoryFields>> historyInFlight =
            new ConcurrentHashMap<>();

    private String benchmarkSymbol() {
        return (primaryBenchmarkSymbol == null || primaryBenchmarkSymbol.isBlank())
                ? NIFTY_SYMBOL
                : primaryBenchmarkSymbol.trim();
    }

    public PortfolioIntelligenceSnapshot build(String portfolioId) {
        return build(portfolioId, true);
    }

    public PortfolioIntelligenceSnapshot build(String portfolioId, boolean includeHistory) {
        UUID id;
        try {
            id = UUID.fromString(portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid portfolioId");
        }
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + portfolioId);
        }
        return buildFromPortfolio(portfolio, includeHistory);
    }

    public PortfolioIntelligenceSnapshot buildFromPortfolio(PortfolioModelV1 portfolio) {
        return buildFromPortfolio(portfolio, true, null, null, true);
    }

    public PortfolioIntelligenceSnapshot buildFromPortfolio(PortfolioModelV1 portfolio, boolean includeHistory) {
        return buildFromPortfolio(portfolio, includeHistory, null, null, true);
    }

    /**
     * @param responsePortfolioId optional id stamped on the snapshot (e.g. {@code ALL})
     * @param historyCacheKey optional Redis/history key (e.g. {@code user:{id}:all}); falls back to response id / portfolio UUID
     */
    public PortfolioIntelligenceSnapshot buildFromPortfolio(
            PortfolioModelV1 portfolio,
            boolean includeHistory,
            String responsePortfolioId,
            String historyCacheKey) {
        return buildFromPortfolio(portfolio, includeHistory, responsePortfolioId, historyCacheKey, true);
    }

    /**
     * @param fetchHistoryIfMiss when false, Redis/L1 hist only; joins in-flight if present, else empty
     */
    public PortfolioIntelligenceSnapshot buildFromPortfolio(
            PortfolioModelV1 portfolio,
            boolean includeHistory,
            String responsePortfolioId,
            String historyCacheKey,
            boolean fetchHistoryIfMiss) {
        return buildFromPortfolio(
                portfolio, includeHistory, responsePortfolioId, historyCacheKey, fetchHistoryIfMiss, 0L);
    }

    /**
     * @param historyTimeoutOverrideMs when &gt; 0, used instead of {@code history-timeout-ms} for network fetch
     */
    public PortfolioIntelligenceSnapshot buildFromPortfolio(
            PortfolioModelV1 portfolio,
            boolean includeHistory,
            String responsePortfolioId,
            String historyCacheKey,
            boolean fetchHistoryIfMiss,
            long historyTimeoutOverrideMs) {
        String portfolioId = responsePortfolioId != null
                ? responsePortfolioId
                : (portfolio.getId() != null ? portfolio.getId().toString() : null);
        String histKey = historyCacheKey != null
                ? historyCacheKey
                : portfolioId;
        List<EquityModel> equities = portfolio.getEquityModels() != null
                ? portfolio.getEquityModels() : List.of();
        List<AssetModel> mutualFunds = portfolio.getMutualFunds() != null
                ? portfolio.getMutualFunds() : List.of();
        List<AssetModel> bonds = portfolio.getBonds() != null
                ? portfolio.getBonds() : List.of();
        List<AssetModel> commodities = portfolio.getCommodities() != null
                ? portfolio.getCommodities() : List.of();
        List<AssetModel> cash = portfolio.getCash() != null
                ? portfolio.getCash() : List.of();

        boolean anyHoldings = !equities.isEmpty()
                || !mutualFunds.isEmpty()
                || !bonds.isEmpty()
                || !commodities.isEmpty()
                || !cash.isEmpty();
        if (!anyHoldings) {
            return PortfolioIntelligenceSnapshot.builder()
                    .portfolioId(portfolioId)
                    .holdings(List.of())
                    .totalValue(0)
                    .holdingsCount(0)
                    .distinctSectors(0)
                    .historyPoints(0)
                    .build();
        }

        List<String> symbols = equities.stream()
                .filter(e -> e != null && e.getSymbol() != null && !e.getSymbol().isBlank())
                .filter(e -> e.getQuantity() != null && e.getQuantity() > 0)
                .map(e -> SymbolResolver.normalize(e.getSymbol()))
                .distinct()
                .collect(Collectors.toList());

        Map<String, MarketData> marketData = Map.of();
        if (!symbols.isEmpty()) {
            try {
                Map<String, MarketData> raw = marketDataService.getMarketData(symbols);
                Map<String, MarketData> normalized = new HashMap<>();
                if (raw != null) {
                    for (Map.Entry<String, MarketData> entry : raw.entrySet()) {
                        if (entry.getValue() != null) {
                            String key = entry.getKey().contains(":")
                                    ? entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                                    : entry.getKey();
                            normalized.put(SymbolResolver.normalize(key), entry.getValue());
                        }
                    }
                }
                marketData = normalized;
            } catch (Exception e) {
                log.warn("Failed to prefetch market data for intelligence snapshot: {}", e.getMessage());
            }
        }

        Map<String, SecurityModel> securityDetails = Map.of();
        if (!symbols.isEmpty()) {
            try {
                securityDetails = securityDetailsService.getSecurityDetails(symbols);
            } catch (Exception e) {
                log.warn("Failed to prefetch security details for intelligence snapshot: {}", e.getMessage());
            }
        }

        List<PortfolioIntelligenceSnapshot.Holding> holdings = new ArrayList<>();
        Map<String, Double> quantities = new HashMap<>();
        int droppedNoPrice = 0;
        for (EquityModel eq : equities) {
            if (eq == null || eq.getSymbol() == null || eq.getSymbol().isBlank()) {
                continue;
            }
            if (eq.getQuantity() == null || eq.getQuantity() <= 0) {
                continue;
            }
            String sym = SymbolResolver.normalize(eq.getSymbol());
            MarketData md = marketData.get(sym);
            double price = AllocationUtils.resolvePrice(md);
            if (price <= 0 && eq.getAvgBuyingPrice() != null && eq.getAvgBuyingPrice() > 0) {
                price = eq.getAvgBuyingPrice();
            }
            if (price <= 0) {
                droppedNoPrice++;
                continue;
            }
            double value = price * eq.getQuantity();
            SecurityModel sec = securityDetails.get(sym);
            String sector = resolveSector(eq, sec);
            String industry = resolveIndustry(eq, sec);
            String marketCap = resolveMarketCap(eq, sec);

            holdings.add(PortfolioIntelligenceSnapshot.Holding.builder()
                    .symbol(sym)
                    .value(value)
                    .weightPct(0)
                    .sector(sector)
                    .industry(industry)
                    .marketCap(marketCap)
                    .assetClass(HealthScoreEngine.ASSET_EQUITY)
                    .build());
            quantities.put(sym, eq.getQuantity());
        }

        droppedNoPrice += appendAssetHoldings(holdings, mutualFunds, HealthScoreEngine.ASSET_MUTUAL_FUND);
        droppedNoPrice += appendAssetHoldings(holdings, bonds, HealthScoreEngine.ASSET_FIXED_INCOME);
        droppedNoPrice += appendAssetHoldings(holdings, commodities, HealthScoreEngine.ASSET_COMMODITY);
        droppedNoPrice += appendAssetHoldings(holdings, cash, HealthScoreEngine.ASSET_CASH);

        if (droppedNoPrice > 0) {
            log.warn("Intel snapshot portfolioId={} dropped {} holdings with no usable price",
                    portfolioId, droppedNoPrice);
        }

        HistoryFields history = includeHistory
                ? loadHistoryMetrics(histKey, symbols, quantities, fetchHistoryIfMiss, historyTimeoutOverrideMs)
                : HistoryFields.empty();
        return finalizeSnapshot(
                portfolioId,
                holdings,
                history.historyPoints,
                history.portRetPct,
                history.niftyRetPct,
                history.dailyVolPct,
                history.beta,
                history.portfolioDailyReturns,
                history.niftyDailyReturns);
    }

    /**
     * Maps Option A non-equity lists into snapshot holdings using stored value/price.
     * @return count of rows skipped for missing price/value
     */
    private int appendAssetHoldings(
            List<PortfolioIntelligenceSnapshot.Holding> holdings,
            List<AssetModel> assets,
            String assetClass) {
        if (assets == null || assets.isEmpty()) {
            return 0;
        }
        int dropped = 0;
        for (AssetModel asset : assets) {
            if (asset == null) {
                continue;
            }
            double value = resolveAssetValue(asset);
            if (value <= 0) {
                dropped++;
                continue;
            }
            String sym = asset.getSymbol();
            if (sym == null || sym.isBlank()) {
                sym = asset.getName() != null ? asset.getName() : assetClass;
            }
            holdings.add(PortfolioIntelligenceSnapshot.Holding.builder()
                    .symbol(SymbolResolver.normalize(sym))
                    .value(value)
                    .weightPct(0)
                    .sector(null)
                    .industry(null)
                    .marketCap(null)
                    .assetClass(assetClass)
                    .build());
        }
        return dropped;
    }

    private static double resolveAssetValue(AssetModel asset) {
        if (asset.getCurrentValue() != null && asset.getCurrentValue() > 0) {
            return asset.getCurrentValue();
        }
        double qty = asset.getQuantity() != null ? asset.getQuantity() : 0.0;
        if (qty <= 0) {
            return 0.0;
        }
        double price = asset.getCurrentPrice() != null && asset.getCurrentPrice() > 0
                ? asset.getCurrentPrice()
                : (asset.getAvgBuyingPrice() != null ? asset.getAvgBuyingPrice() : 0.0);
        return qty * price;
    }

    private HistoryFields loadHistoryMetrics(
            String portfolioId, List<String> symbols, Map<String, Double> quantities) {
        return loadHistoryMetrics(portfolioId, symbols, quantities, true, 0L);
    }

    private HistoryFields loadHistoryMetrics(
            String portfolioId,
            List<String> symbols,
            Map<String, Double> quantities,
            boolean fetchHistoryIfMiss) {
        return loadHistoryMetrics(portfolioId, symbols, quantities, fetchHistoryIfMiss, 0L);
    }

    private HistoryFields loadHistoryMetrics(
            String portfolioId,
            List<String> symbols,
            Map<String, Double> quantities,
            boolean fetchHistoryIfMiss,
            long historyTimeoutOverrideMs) {
        if (symbols == null || symbols.isEmpty() || quantities == null || quantities.isEmpty()) {
            log.info("Intel hist empty portfolioId={} reason=no_symbols", portfolioId);
            return HistoryFields.empty();
        }

        var cached = historyCache.get(portfolioId);
        if (cached.isPresent()) {
            CachedIntelligenceHistory c = cached.get();
            log.debug("Intel hist cache hit portfolioId={} historyPoints={} beta={}",
                    portfolioId, c.getHistoryPoints(), c.getBeta());
            return HistoryFields.fromCache(c);
        }

        String flightKey = portfolioId != null ? portfolioId : UUID.randomUUID().toString();
        long timeoutMs = effectiveHistoryTimeoutMs(historyTimeoutOverrideMs);

        if (!fetchHistoryIfMiss) {
            CompletableFuture<HistoryFields> inflight = historyInFlight.get(flightKey);
            if (inflight != null) {
                return awaitHistory(inflight, timeoutMs, portfolioId, "inflight_join");
            }
            log.info("Intel hist empty portfolioId={} reason=cache_only_no_inflight", portfolioId);
            return HistoryFields.empty();
        }

        CompletableFuture<HistoryFields> work = historyInFlight.computeIfAbsent(flightKey, key -> {
            CompletableFuture<HistoryFields> fetch = CompletableFuture.supplyAsync(
                    () -> fetchHistory(symbols, quantities));
            fetch.whenComplete((loaded, err) -> {
                try {
                    HistoryFields result = err != null || loaded == null
                            ? HistoryFields.empty()
                            : loaded;
                    if (err != null) {
                        log.info("Intel hist empty portfolioId={} reason=fetch_failed detail={}",
                                portfolioId, err.getMessage());
                    } else if (result.historyPoints == 0) {
                        log.info("Intel hist empty portfolioId={} reason=empty_after_fetch", portfolioId);
                    }
                    if (result.historyPoints >= HealthScoreConstants.MIN_HISTORY_POINTS
                            && result.beta != null
                            && Double.isFinite(result.beta)
                            && portfolioId != null) {
                        historyCache.put(portfolioId, CachedIntelligenceHistory.builder()
                                .historyPoints(result.historyPoints)
                                .portRetPct(result.portRetPct)
                                .niftyRetPct(result.niftyRetPct)
                                .dailyVolPct(result.dailyVolPct)
                                .beta(result.beta)
                                .computedAtEpochMs(System.currentTimeMillis())
                                .build());
                        onHistoryWarmed(portfolioId);
                    }
                } finally {
                    historyInFlight.remove(key, fetch);
                }
            });
            return fetch;
        });

        return awaitHistory(work, timeoutMs, portfolioId, "timeout");
    }

    /**
     * Soft-wait for hist without cancelling the shared in-flight future.
     * Intel may return empty at 800ms while MD continues; stress joins the same future.
     */
    private HistoryFields awaitHistory(
            CompletableFuture<HistoryFields> work,
            long timeoutMs,
            String portfolioId,
            String timeoutReason) {
        try {
            CompletableFuture<HistoryFields> softEmpty = new CompletableFuture<>();
            softEmpty.completeOnTimeout(HistoryFields.empty(), timeoutMs, TimeUnit.MILLISECONDS);
            HistoryFields result = work.applyToEither(softEmpty, v -> v != null ? v : HistoryFields.empty())
                    .join();
            if (result.historyPoints == 0 && !work.isDone()) {
                log.info("Intel hist empty portfolioId={} reason={} timeoutMs={}",
                        portfolioId, timeoutReason, timeoutMs);
            }
            return result;
        } catch (Exception e) {
            log.info("Intel hist empty portfolioId={} reason={}_failed detail={}",
                    portfolioId, timeoutReason, e.getMessage());
            return HistoryFields.empty();
        }
    }

    private long effectiveHistoryTimeoutMs(long historyTimeoutOverrideMs) {
        if (historyTimeoutOverrideMs > 0) {
            return historyTimeoutOverrideMs;
        }
        return historyTimeoutMs > 0 ? historyTimeoutMs : HISTORY_TIMEOUT_MS;
    }

    /** Optional hook so stress L1 can drop sticky ASSUMED entries after hist warms. */
    private volatile java.util.function.Consumer<String> historyWarmedListener;

    public void setHistoryWarmedListener(java.util.function.Consumer<String> listener) {
        this.historyWarmedListener = listener;
    }

    private void onHistoryWarmed(String portfolioId) {
        java.util.function.Consumer<String> listener = historyWarmedListener;
        if (listener != null && portfolioId != null) {
            try {
                listener.accept(portfolioId);
            } catch (Exception e) {
                log.debug("historyWarmedListener failed: {}", e.getMessage());
            }
        }
    }

    public long getHistoryTimeoutStressMs() {
        return historyTimeoutStressMs > 0 ? historyTimeoutStressMs : HISTORY_TIMEOUT_STRESS_MS;
    }

    private HistoryFields fetchHistory(List<String> symbols, Map<String, Double> quantities) {
        LocalDate to = LocalDate.now();
        int lookback = historyLookbackDays > 0 ? historyLookbackDays : HISTORY_LOOKBACK_DAYS;
        LocalDate from = to.minusDays(lookback);
        List<String> histSymbols = new ArrayList<>(symbols);
        String benchmark = benchmarkSymbol();
        // Do not put NIFTY on the EQ batch — INDEX overlay supplies the benchmark.

        HistoricalDataRequest histReq = HistoricalDataRequest.builder()
                .symbols(String.join(",", histSymbols))
                .fromDate(from.toString())
                .toDate(to.toString())
                .filterType(FilterType.ALL.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .isIndexSymbol(false)
                .continuous(false)
                .interval(TimeFrame.DAY.getValue())
                .build();

        Map<String, MarketData> raw = marketDataService.getHistoricalData(histReq);
        Map<String, MarketData> normalized = normalizeHistorical(raw);

        // Prefer INDEX bars for the benchmark — EQ "NIFTY 50" is often missing or flat,
        // which yields historyPoints>0 with beta=null → sticky ASSUMED_ONE.
        // Overlay writes the INDEX series under every alias so first-key-wins cannot
        // keep a flat EQ "NIFTY 50" when INDEX arrived as "NIFTY50".
        String benchmarkKey = resolveBenchmarkKey(normalized);
        Map<String, MarketData> indexBars = fetchBenchmarkAsIndex(benchmark, from, to);
        MarketData indexSeries = pickUsableBenchmarkSeries(indexBars);
        if (indexSeries != null) {
            applyBenchmarkSeriesToAliases(normalized, indexSeries);
            normalized.putAll(indexBars);
            benchmarkKey = resolveBenchmarkKey(normalized);
            log.info("Intel hist benchmark filled via INDEX symbol={}", benchmark);
        } else {
            MarketData eqSeries = normalized.get(benchmarkKey);
            if (!hasNonZeroCloseVariance(eqSeries)) {
                dropBenchmarkAliases(normalized);
                benchmarkKey = resolveBenchmarkKey(normalized);
                log.info("Intel hist empty reason=no_benchmark symbol={}", benchmark);
            }
        }

        if (normalized.isEmpty()) {
            log.info("Intel hist empty reason=empty_md");
            return HistoryFields.empty();
        }

        IntelligenceHistoryMetrics.Result metrics =
                IntelligenceHistoryMetrics.compute(normalized, quantities, benchmarkKey);
        if (metrics.historyPoints() == 0) {
            log.info("Intel hist empty reason=coverage_or_align benchmarkKey={}", benchmarkKey);
        } else if (metrics.beta() == null) {
            log.info("Intel hist beta null portfolio points={} benchmarkKey={} (market var/cov undefined)",
                    metrics.historyPoints(), benchmarkKey);
        }
        return new HistoryFields(
                metrics.historyPoints(),
                metrics.portRetPct(),
                metrics.niftyRetPct(),
                metrics.dailyVolPct(),
                metrics.beta(),
                metrics.portfolioDailyReturns(),
                metrics.niftyDailyReturns());
    }

    private Map<String, MarketData> fetchBenchmarkAsIndex(String benchmark, LocalDate from, LocalDate to) {
        try {
            HistoricalDataRequest indexReq = HistoricalDataRequest.builder()
                    .symbols(benchmark)
                    .fromDate(from.toString())
                    .toDate(to.toString())
                    .filterType(FilterType.ALL.getValue())
                    .instrumentType(InstrumentType.INDEX.getValue())
                    .isIndexSymbol(true)
                    .continuous(false)
                    .interval(TimeFrame.DAY.getValue())
                    .build();
            return normalizeHistorical(marketDataService.getHistoricalData(indexReq));
        } catch (Exception e) {
            log.warn("Intel hist INDEX benchmark fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, MarketData> normalizeHistorical(Map<String, MarketData> raw) {
        Map<String, MarketData> normalized = new HashMap<>();
        if (raw == null) {
            return normalized;
        }
        for (Map.Entry<String, MarketData> entry : raw.entrySet()) {
            if (entry.getValue() != null) {
                String key = entry.getKey().contains(":")
                        ? entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                        : entry.getKey();
                normalized.put(SymbolResolver.normalize(key), entry.getValue());
            }
        }
        return normalized;
    }

    private static boolean hasHistoricalPoints(MarketData md) {
        return md != null && md.getDataPoints() != null && !md.getDataPoints().isEmpty();
    }

    private void applyBenchmarkSeriesToAliases(Map<String, MarketData> normalized, MarketData series) {
        for (String alias : benchmarkAliasKeys()) {
            if (alias != null && !alias.isBlank()) {
                normalized.put(alias, series);
            }
        }
    }

    private void dropBenchmarkAliases(Map<String, MarketData> normalized) {
        for (String alias : benchmarkAliasKeys()) {
            if (alias != null) {
                normalized.remove(alias);
            }
        }
    }

    private List<String> benchmarkAliasKeys() {
        String configured = benchmarkSymbol();
        String configuredNorm = SymbolResolver.normalize(configured);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(configuredNorm);
        keys.add(configured);
        String upper = configured.toUpperCase(Locale.ROOT);
        if (upper.contains("NIFTY")) {
            keys.add(SymbolResolver.normalize("NIFTY50"));
            keys.add("NIFTY 50");
            keys.add("NIFTY50");
        }
        if (upper.contains("SENSEX") || upper.contains("BSE")) {
            keys.add(SymbolResolver.normalize("SENSEX"));
            keys.add("SENSEX");
            keys.add("BSE SENSEX");
        }
        return new ArrayList<>(keys);
    }

    private static MarketData pickUsableBenchmarkSeries(Map<String, MarketData> bars) {
        if (bars == null || bars.isEmpty()) {
            return null;
        }
        for (MarketData md : bars.values()) {
            if (hasNonZeroCloseVariance(md)) {
                return md;
            }
        }
        return null;
    }

    private static boolean hasNonZeroCloseVariance(MarketData md) {
        if (!hasHistoricalPoints(md)) {
            return false;
        }
        Double first = null;
        for (MarketData.MarketDataPoint p : md.getDataPoints()) {
            if (p == null || p.getOhlcData() == null) {
                continue;
            }
            Double close = p.getOhlcData().getClose();
            if (close == null || !Double.isFinite(close) || close <= 0) {
                continue;
            }
            if (first == null) {
                first = close;
            } else if (Math.abs(close - first) > 1e-9) {
                return true;
            }
        }
        return false;
    }

    private String resolveBenchmarkKey(Map<String, MarketData> normalized) {
        String configured = benchmarkSymbol();
        String configuredNorm = SymbolResolver.normalize(configured);
        List<String> candidates = new ArrayList<>();
        candidates.add(configuredNorm);
        candidates.add(configured);
        String upper = configured.toUpperCase(Locale.ROOT);
        if (upper.contains("NIFTY")) {
            candidates.add(SymbolResolver.normalize("NIFTY50"));
            candidates.add("NIFTY 50");
            candidates.add("NIFTY50");
        }
        if (upper.contains("SENSEX") || upper.contains("BSE")) {
            candidates.add(SymbolResolver.normalize("SENSEX"));
            candidates.add("SENSEX");
            candidates.add("BSE SENSEX");
        }
        for (String candidate : candidates) {
            if (normalized.containsKey(candidate)) {
                return candidate;
            }
        }
        for (String key : normalized.keySet()) {
            if (key == null) {
                continue;
            }
            String ku = key.toUpperCase(Locale.ROOT);
            if (upper.contains("SENSEX") && ku.contains("SENSEX")) {
                return key;
            }
            if (upper.contains("NIFTY") && ku.contains("NIFTY") && ku.contains("50")) {
                return key;
            }
        }
        return configuredNorm;
    }

    private record HistoryFields(
            int historyPoints,
            Double portRetPct,
            Double niftyRetPct,
            Double dailyVolPct,
            Double beta,
            List<Double> portfolioDailyReturns,
            List<Double> niftyDailyReturns) {
        static HistoryFields empty() {
            return new HistoryFields(0, null, null, null, null, null, null);
        }

        static HistoryFields fromCache(CachedIntelligenceHistory c) {
            return new HistoryFields(
                    c.getHistoryPoints(),
                    c.getPortRetPct(),
                    c.getNiftyRetPct(),
                    c.getDailyVolPct(),
                    c.getBeta(),
                    null,
                    null);
        }
    }

    /**
     * Recompute weights and aggregate metrics after what-if mutations.
     */
    public static PortfolioIntelligenceSnapshot finalizeSnapshot(
            String portfolioId,
            List<PortfolioIntelligenceSnapshot.Holding> holdings,
            int historyPoints,
            Double portRetPct,
            Double niftyRetPct,
            Double dailyVolPct,
            Double beta) {
        return finalizeSnapshot(portfolioId, holdings, historyPoints, portRetPct, niftyRetPct,
                dailyVolPct, beta, null, null);
    }

    public static PortfolioIntelligenceSnapshot finalizeSnapshot(
            String portfolioId,
            List<PortfolioIntelligenceSnapshot.Holding> holdings,
            int historyPoints,
            Double portRetPct,
            Double niftyRetPct,
            Double dailyVolPct,
            Double beta,
            List<Double> portfolioDailyReturns,
            List<Double> niftyDailyReturns) {

        double total = holdings.stream().mapToDouble(PortfolioIntelligenceSnapshot.Holding::getValue).sum();
        for (PortfolioIntelligenceSnapshot.Holding h : holdings) {
            double w = total > 0 ? (h.getValue() / total) * 100.0 : 0.0;
            h.setWeightPct(round2(w));
        }

        int holdingsCount = holdings.size();
        Set<String> sectors = new HashSet<>();
        double top1 = 0;
        Map<String, Double> sectorSums = new HashMap<>();
        double liquidValue = 0;

        for (PortfolioIntelligenceSnapshot.Holding h : holdings) {
            if (h.getSector() != null && !h.getSector().isBlank()) {
                sectors.add(h.getSector());
                sectorSums.merge(h.getSector(), h.getWeightPct(), Double::sum);
            }
            top1 = Math.max(top1, h.getWeightPct());
            if (isLiquidCap(h.getMarketCap())) {
                liquidValue += h.getValue();
            }
        }

        String maxSectorName = null;
        double maxSectorPct = 0;
        for (Map.Entry<String, Double> e : sectorSums.entrySet()) {
            if (e.getValue() > maxSectorPct) {
                maxSectorPct = e.getValue();
                maxSectorName = e.getKey();
            }
        }

        double liquidSharePct = total > 0 ? round2((liquidValue / total) * 100.0) : 0.0;

        boolean measuredHist = historyPoints >= HealthScoreConstants.MIN_HISTORY_POINTS;
        Double measuredBeta = measuredHist && beta != null && Double.isFinite(beta) ? beta : null;

        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId(portfolioId)
                .holdings(holdings)
                .totalValue(round2(total))
                .holdingsCount(holdingsCount)
                .distinctSectors(sectors.size())
                .top1Pct(round2(top1))
                .maxSectorPct(round2(maxSectorPct))
                .maxSectorName(maxSectorName)
                .liquidSharePct(liquidSharePct)
                .historyPoints(historyPoints)
                .portRetPct(measuredHist ? portRetPct : null)
                .niftyRetPct(measuredHist ? niftyRetPct : null)
                .dailyVolPct(measuredHist ? dailyVolPct : null)
                .beta(measuredBeta)
                .portfolioDailyReturns(portfolioDailyReturns)
                .niftyDailyReturns(niftyDailyReturns)
                .build();
    }

    private static boolean isLiquidCap(String marketCap) {
        if (marketCap == null) {
            return false;
        }
        String u = marketCap.toUpperCase(Locale.ROOT);
        return u.contains("LARGE") || u.contains("MID");
    }

    private static String resolveSector(EquityModel eq, SecurityModel sec) {
        String fromEq = usableMeta(eq.getSector());
        if (fromEq != null) {
            return fromEq;
        }
        if (sec != null && sec.getMetadata() != null) {
            String fromSec = usableMeta(sec.getMetadata().getSector());
            if (fromSec != null) {
                return fromSec;
            }
        }
        return "Unknown";
    }

    private static String resolveIndustry(EquityModel eq, SecurityModel sec) {
        String fromEq = usableMeta(eq.getIndustry());
        if (fromEq != null) {
            return fromEq;
        }
        if (sec != null && sec.getMetadata() != null) {
            String fromSec = usableMeta(sec.getMetadata().getIndustry());
            if (fromSec != null) {
                return fromSec;
            }
        }
        return "Unknown";
    }

    /** Blank, dash, or literal Unknown → treat as missing so security meta can enrich. */
    static String usableMeta(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty() || "-".equals(t) || "Unknown".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    private static String resolveMarketCap(EquityModel eq, SecurityModel sec) {
        if (eq.getMarketCap() != null && !eq.getMarketCap().isBlank()) {
            return normalizeCapLabel(eq.getMarketCap());
        }
        if (sec != null && sec.getMetadata() != null && sec.getMetadata().getMarketCapType() != null) {
            MarketCapType t = sec.getMetadata().getMarketCapType();
            return t.getName();
        }
        return "UNKNOWN";
    }

    private static String normalizeCapLabel(String raw) {
        String u = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (u.contains("LARGE")) {
            return MarketCapType.LARGE_CAP.getName();
        }
        if (u.contains("MID")) {
            return MarketCapType.MID_CAP.getName();
        }
        if (u.contains("SMALL")) {
            return MarketCapType.SMALL_CAP.getName();
        }
        if (u.contains("MICRO")) {
            return MarketCapType.MICRO_CAP.getName();
        }
        return u;
    }

    static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
