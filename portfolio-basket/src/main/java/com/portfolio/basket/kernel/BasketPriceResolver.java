package com.portfolio.basket.kernel;

import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private final MarketDataService marketDataService;

    public Map<String, Double> fetchPricesWithHoldingsFallback(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings) {
        Map<String, Double> prices = new HashMap<>();
        if (symbolsToFetch == null || symbolsToFetch.isEmpty()) {
            return prices;
        }
        try {
            log.info("basket.prices.fetch symbols={}", symbolsToFetch.size());
            Map<String, Double> fetched = marketDataService.getCurrentPrices(new ArrayList<>(symbolsToFetch));
            if (fetched != null) {
                prices.putAll(fetched);
            }
            log.info("basket.prices.done requested={} resolved={}", symbolsToFetch.size(), prices.size());
        } catch (Exception e) {
            log.warn("Failed to fetch live prices for symbols: {}", e.getMessage());
        }
        applyHoldingsFallback(prices, allUserHoldings);
        return prices;
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

    private void applyHoldingsFallback(Map<String, Double> prices, List<EquityHoldings> allUserHoldings) {
        if (allUserHoldings == null) {
            return;
        }
        for (EquityHoldings h : allUserHoldings) {
            if (h.getSymbol() == null) {
                continue;
            }
            Double existingPrice = prices.get(h.getSymbol());
            if (existingPrice == null || existingPrice <= 0) {
                if (h.getCurrentPrice() != null && h.getCurrentPrice() > 0) {
                    prices.put(h.getSymbol(), h.getCurrentPrice());
                } else if (h.getAverageBuyingPrice() != null && h.getAverageBuyingPrice() > 0) {
                    prices.put(h.getSymbol(), h.getAverageBuyingPrice());
                }
            }
        }
    }
}
