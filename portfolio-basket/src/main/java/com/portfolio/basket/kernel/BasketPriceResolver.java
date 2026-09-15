package com.portfolio.basket.kernel;

import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketPriceResolver {

    public static final String QUALITY_LIVE = "LIVE";
    public static final String QUALITY_STALE = "STALE";
    public static final String QUALITY_PREV_CLOSE = "PREV_CLOSE";
    public static final String QUALITY_COST_BASIS = "COST_BASIS";
    public static final String QUALITY_MISSING = "MISSING";

    private final MarketDataService marketDataService;

    @Data
    @Builder
    public static class ResolvedPrice {
        private Double price;
        private String quality;
        private Instant asOf;
    }

    public Map<String, Double> fetchPricesWithHoldingsFallback(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings) {
        return fetchPricesWithHoldingsFallback(symbolsToFetch, allUserHoldings, false);
    }

    public Map<String, Double> fetchPricesWithHoldingsFallback(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings, boolean skipHistRepair) {
        Map<String, ResolvedPrice> resolved =
                fetchResolvedPrices(symbolsToFetch, allUserHoldings, skipHistRepair);
        Map<String, Double> prices = new HashMap<>();
        for (Map.Entry<String, ResolvedPrice> e : resolved.entrySet()) {
            if (e.getValue() != null && e.getValue().getPrice() != null && e.getValue().getPrice() > 0) {
                prices.put(e.getKey(), e.getValue().getPrice());
            }
        }
        return prices;
    }

    public Map<String, ResolvedPrice> fetchResolvedPrices(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings) {
        return fetchResolvedPrices(symbolsToFetch, allUserHoldings, false);
    }

    public Map<String, ResolvedPrice> fetchResolvedPrices(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings, boolean skipHistRepair) {
        Map<String, ResolvedPrice> resolved = new HashMap<>();
        if (symbolsToFetch == null || symbolsToFetch.isEmpty()) {
            return resolved;
        }
        try {
            log.info("basket.prices.fetch symbols={} skipHistRepair={}", symbolsToFetch.size(), skipHistRepair);
            Map<String, MarketData> market =
                    marketDataService.getMarketData(new ArrayList<>(symbolsToFetch), skipHistRepair);
            if (market != null) {
                for (Map.Entry<String, MarketData> entry : market.entrySet()) {
                    ResolvedPrice rp = fromMarketData(entry.getValue());
                    if (rp != null) {
                        resolved.put(entry.getKey(), rp);
                    }
                }
            }
            log.info("basket.prices.done requested={} resolved={}", symbolsToFetch.size(), resolved.size());
        } catch (Exception e) {
            log.warn("Failed to fetch live prices for symbols: {}", e.getMessage());
            try {
                Map<String, Double> fetched = marketDataService.getCurrentPrices(new ArrayList<>(symbolsToFetch));
                if (fetched != null) {
                    for (Map.Entry<String, Double> entry : fetched.entrySet()) {
                        if (entry.getValue() != null && entry.getValue() > 0) {
                            resolved.put(entry.getKey(), ResolvedPrice.builder()
                                    .price(entry.getValue())
                                    .quality(QUALITY_STALE)
                                    .asOf(null)
                                    .build());
                        }
                    }
                }
            } catch (Exception nested) {
                log.warn("Price map fallback also failed: {}", nested.getMessage());
            }
        }
        applyHoldingsFallback(resolved, allUserHoldings);
        return resolved;
    }

    private ResolvedPrice fromMarketData(MarketData md) {
        if (md == null) {
            return null;
        }
        Double price = null;
        String quality = QUALITY_MISSING;
        if (md.getLastPrice() != null && md.getLastPrice() > 0) {
            price = md.getLastPrice();
            quality = md.getTimestamp() != null ? QUALITY_LIVE : QUALITY_STALE;
        } else if (md.getPreviousClose() != null && md.getPreviousClose() > 0) {
            price = md.getPreviousClose();
            quality = QUALITY_PREV_CLOSE;
        } else if (md.getOhlc() != null && md.getOhlc().getClose() > 0) {
            price = md.getOhlc().getClose();
            quality = QUALITY_STALE;
        }
        if (price == null) {
            return null;
        }
        return ResolvedPrice.builder()
                .price(price)
                .quality(quality)
                .asOf(md.getTimestamp())
                .build();
    }

    public Set<String> unionSymbols(Set<String> etfSymbols, List<EquityHoldings> userHoldings) {
        Set<String> symbols = new HashSet<>();
        if (etfSymbols != null) {
            etfSymbols.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .forEach(symbols::add);
        }
        if (userHoldings != null) {
            for (EquityHoldings h : userHoldings) {
                if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                    symbols.add(h.getSymbol());
                }
            }
        }
        return symbols;
    }

    private void applyHoldingsFallback(Map<String, ResolvedPrice> resolved, List<EquityHoldings> allUserHoldings) {
        if (allUserHoldings == null) {
            return;
        }
        for (EquityHoldings h : allUserHoldings) {
            if (h.getSymbol() == null) {
                continue;
            }
            ResolvedPrice existing = resolved.get(h.getSymbol());
            if (existing != null && existing.getPrice() != null && existing.getPrice() > 0) {
                continue;
            }
            if (h.getCurrentPrice() != null && h.getCurrentPrice() > 0) {
                resolved.put(h.getSymbol(), ResolvedPrice.builder()
                        .price(h.getCurrentPrice())
                        .quality(QUALITY_STALE)
                        .asOf(null)
                        .build());
            } else if (h.getAverageBuyingPrice() != null && h.getAverageBuyingPrice() > 0) {
                resolved.put(h.getSymbol(), ResolvedPrice.builder()
                        .price(h.getAverageBuyingPrice())
                        .quality(QUALITY_COST_BASIS)
                        .asOf(null)
                        .build());
            }
        }
    }

    public static void applyQuality(com.portfolio.basket.model.BasketOpportunity.BasketItem item,
                                    ResolvedPrice resolved) {
        if (item == null) {
            return;
        }
        if (resolved == null || resolved.getPrice() == null || resolved.getPrice() <= 0) {
            item.setPriceQuality(QUALITY_MISSING);
            item.setPriceAsOf(null);
            return;
        }
        item.setLastPrice(resolved.getPrice());
        item.setPriceQuality(resolved.getQuality() != null ? resolved.getQuality() : QUALITY_STALE);
        item.setPriceAsOf(resolved.getAsOf());
    }
}
