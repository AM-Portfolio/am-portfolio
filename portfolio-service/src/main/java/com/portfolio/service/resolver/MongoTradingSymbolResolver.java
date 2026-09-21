package com.portfolio.service.resolver;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.model.resolver.TradingSymbolResolver;
import com.portfolio.model.util.SymbolResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves ISIN / company-name → NSE/BSE trading symbol using Market Data
 * {@code POST /v1/securities/batch-search} (same API basket ETF enrichment uses).
 *
 * <p>Fail-open: if API is unavailable or the instrument is missing, returns ISIN
 * (preferred) or the normalized input so holdings are never dropped during save.
 */
@Service
@Slf4j
public class MongoTradingSymbolResolver implements TradingSymbolResolver {

    private final MarketDataApiClient marketDataApiClient;

    @Autowired
    public MongoTradingSymbolResolver(MarketDataApiClient marketDataApiClient) {
        this.marketDataApiClient = marketDataApiClient;
    }

    @Override
    public String resolveTradingSymbol(String symbol, String isin) {
        String normalizedSymbol = symbol != null ? SymbolResolver.normalize(symbol) : null;

        // Compact ticker — no lookup. Company names / free text must use ISIN.
        if (TradingSymbolResolver.looksLikeTradingTicker(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }

        String isinToResolve = pickIsin(normalizedSymbol, isin);
        if (isinToResolve == null) {
            return fallbackIdentifier(normalizedSymbol, isin);
        }

        Map<String, String> resolved = resolveTradingSymbols(List.of(isinToResolve));
        String ticker = resolved.get(isinToResolve);
        if (ticker != null && !ticker.isBlank()) {
            return ticker;
        }

        return fallbackIdentifier(normalizedSymbol, isin);
    }

    private String pickIsin(String normalizedSymbol, String isin) {
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        if (TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }
        return null;
    }

    /**
     * Resolves multiple ISIN codes via securities batch-search in one call.
     *
     * @return Map ISIN → trading symbol
     */
    @Override
    public Map<String, String> resolveTradingSymbols(List<String> isins) {
        if (marketDataApiClient == null || isins == null || isins.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> cleanedIsins = isins.stream()
                    .filter(i -> i != null && !i.isBlank())
                    .map(i -> i.trim().toUpperCase())
                    .distinct()
                    .collect(Collectors.toList());

            if (cleanedIsins.isEmpty()) {
                return Map.of();
            }

            Map<String, String> response = marketDataApiClient.resolveTickersByIsins(cleanedIsins).block();
            if (response == null || response.isEmpty()) {
                return Map.of();
            }
            Map<String, String> result = new HashMap<>();
            response.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    result.put(k.trim().toUpperCase(), v.trim().toUpperCase());
                }
            });
            return result;
        } catch (Exception ex) {
            log.warn("Batch ISIN lookup via securities batch-search failed: {}", ex.getMessage());
        }
        return Map.of();
    }

    private String fallbackIdentifier(String normalizedSymbol, String isin) {
        // Prefer ISIN over free-text company names so historical/live can resolve via instruments.
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            return normalizedSymbol.trim().toUpperCase();
        }
        return null;
    }
}
