package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.util.SymbolResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link PortfolioIntelligenceSnapshot} from portfolio holdings + live prices.
 * History/vol/beta omitted when series unavailable ({@code historyPoints = 0}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioIntelligenceSnapshotFactory {

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final SecurityDetailsService securityDetailsService;

    public PortfolioIntelligenceSnapshot build(String portfolioId) {
        UUID id = UUID.fromString(portfolioId);
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
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
        }

        return finalizeSnapshot(portfolioId, holdings, 0, null, null, null, null);
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
