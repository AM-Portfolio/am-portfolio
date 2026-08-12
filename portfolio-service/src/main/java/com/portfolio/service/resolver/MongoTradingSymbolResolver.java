package com.portfolio.service.resolver;

import com.portfolio.model.resolver.TradingSymbolResolver;
import com.portfolio.model.util.SymbolResolver;
import com.portfolio.marketdata.client.MarketDataApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;


/**
 * Resolves ISIN → NSE/BSE trading symbol using Market Data API instead of direct MongoDB queries.
 * This respects microservice database isolation and credentials restriction.
 *
 * <p>Fail-open: if API is unavailable or the instrument is missing, returns the normalized
 * input so holdings are never dropped during save.
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

        // Already a normal ticker — no DB/API lookup needed.
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()
                && !TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }

        String isinToResolve = pickIsin(normalizedSymbol, isin);
        if (isinToResolve == null) {
            return fallbackIdentifier(normalizedSymbol, isin);
        }

        String resolved = lookupTradingSymbolByIsin(isinToResolve);
        if (resolved != null) {
            return resolved;
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

    @SuppressWarnings("rawtypes")
    private String lookupTradingSymbolByIsin(String isin) {
        if (marketDataApiClient == null) {
            log.warn("MarketDataApiClient not available — skipping ISIN lookup for {}", isin);
            return null;
        }
        try {
            // Block synchronously since the TradingSymbolResolver interface is synchronous.
            // This is called inside parsing threads.
            Map response = marketDataApiClient.resolveTickerByIsin(isin).block();
            if (response != null && response.containsKey("symbol")) {
                String symbol = String.valueOf(response.get("symbol"));
                if (symbol != null && !symbol.isBlank()) {
                    return symbol.trim().toUpperCase();
                }
            }
        } catch (Exception ex) {
            // Fail-open: save must continue even if API resolver is down.
            log.warn("ISIN lookup API call failed for {}: {}", isin, ex.getMessage());
        }
        return null;
    }

    /**
     * Resolves multiple ISIN codes to NSE/BSE symbols dynamically in a single batch API call.
     * Respects database isolation boundaries and optimizes network latency.
     *
     * @param isins List of ISIN codes of securities
     * @return Map mapping ISIN to resolved symbol
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<String, String> resolveTradingSymbols(List<String> isins) {
        if (marketDataApiClient == null || isins == null || isins.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> cleanedIsins = isins.stream()
                    .filter(i -> i != null && !i.isBlank())
                    .map(i -> i.trim().toUpperCase())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            if (cleanedIsins.isEmpty()) {
                return Map.of();
            }

            // Execute the bulk request synchronously since the pipeline needs immediate resolving
            Map response = marketDataApiClient.resolveTickersByIsins(cleanedIsins).block();
            if (response != null) {
                Map<String, String> result = new java.util.HashMap<>();
                response.forEach((k, v) -> {
                    if (k != null && v != null) {
                        result.put(String.valueOf(k).trim().toUpperCase(), String.valueOf(v).trim().toUpperCase());
                    }
                });
                return result;
            }
        } catch (Exception ex) {
            log.warn("Batch ISIN lookup API call failed: {}", ex.getMessage());
        }
        return Map.of();
    }

    private String fallbackIdentifier(String normalizedSymbol, String isin) {
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            return normalizedSymbol.trim().toUpperCase();
        }
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        return null;
    }
}

