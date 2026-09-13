package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.model.util.SymbolResolver;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    public static final int HISTORY_LOOKBACK_DAYS = 90;
    public static final long HISTORY_TIMEOUT_MS = 2500L;

    @Value("${portfolio.intelligence.primary-benchmark-symbol:NIFTY 50}")
    private String primaryBenchmarkSymbol;

    @Value("${portfolio.intelligence.history-lookback-days:90}")
    private int historyLookbackDays;

    @Value("${portfolio.intelligence.history-timeout-ms:2500}")
    private long historyTimeoutMs;

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final SecurityDetailsService securityDetailsService;

    private String benchmarkSymbol() {
        return (primaryBenchmarkSymbol == null || primaryBenchmarkSymbol.isBlank())
                ? NIFTY_SYMBOL
                : primaryBenchmarkSymbol.trim();
    }

    public PortfolioIntelligenceSnapshot build(String portfolioId) {
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
        return buildFromPortfolio(portfolio);
    }

    public PortfolioIntelligenceSnapshot buildFromPortfolio(PortfolioModelV1 portfolio) {
        String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : null;
        List<EquityModel> equities = portfolio.getEquityModels();
        if (equities == null || equities.isEmpty()) {
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
                    .build());
            quantities.put(sym, eq.getQuantity());
        }

        if (droppedNoPrice > 0) {
            log.warn("Intel snapshot portfolioId={} dropped {} holdings with no usable price",
                    portfolioId, droppedNoPrice);
        }

        HistoryFields history = loadHistoryMetrics(symbols, quantities);
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

    private HistoryFields loadHistoryMetrics(List<String> symbols, Map<String, Double> quantities) {
        if (symbols == null || symbols.isEmpty() || quantities == null || quantities.isEmpty()) {
            return HistoryFields.empty();
        }
        try {
            long timeoutMs = historyTimeoutMs > 0 ? historyTimeoutMs : HISTORY_TIMEOUT_MS;
            return CompletableFuture.supplyAsync(() -> fetchHistory(symbols, quantities))
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("Intel history timed out or failed after {}ms: {}",
                                timeoutMs, ex.getMessage());
                        return HistoryFields.empty();
                    })
                    .join();
        } catch (Exception e) {
            log.warn("Intel history load failed: {}", e.getMessage());
            return HistoryFields.empty();
        }
    }

    private HistoryFields fetchHistory(List<String> symbols, Map<String, Double> quantities) {
        LocalDate to = LocalDate.now();
        int lookback = historyLookbackDays > 0 ? historyLookbackDays : HISTORY_LOOKBACK_DAYS;
        LocalDate from = to.minusDays(lookback);
        List<String> histSymbols = new ArrayList<>(symbols);
        String benchmark = benchmarkSymbol();
        String benchmarkNorm = SymbolResolver.normalize(benchmark);
        if (histSymbols.stream().noneMatch(s ->
                s.equalsIgnoreCase(benchmarkNorm) || s.equalsIgnoreCase(benchmark))) {
            histSymbols.add(benchmark);
        }

        HistoricalDataRequest histReq = HistoricalDataRequest.builder()
                .symbols(String.join(",", histSymbols))
                .fromDate(from.toString())
                .toDate(to.toString())
                .filterType(FilterType.ALL.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .continuous(false)
                .interval(TimeFrame.DAY.getValue())
                .build();

        Map<String, MarketData> raw = marketDataService.getHistoricalData(histReq);
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

        String benchmarkKey = resolveBenchmarkKey(normalized);
        IntelligenceHistoryMetrics.Result metrics =
                IntelligenceHistoryMetrics.compute(normalized, quantities, benchmarkKey);
        return new HistoryFields(
                metrics.historyPoints(),
                metrics.portRetPct(),
                metrics.niftyRetPct(),
                metrics.dailyVolPct(),
                metrics.beta(),
                metrics.portfolioDailyReturns(),
                metrics.niftyDailyReturns());
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
                .portRetPct(portRetPct)
                .niftyRetPct(niftyRetPct)
                .dailyVolPct(dailyVolPct)
                .beta(beta)
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
