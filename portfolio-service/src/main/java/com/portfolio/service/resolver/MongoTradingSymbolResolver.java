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
 *
 * <p>When an ISIN is present it is always preferred over a broker ticker that only "looks"
 * like a symbol (e.g. IDEA + INE669E01016 → VODAFONEIDEA).
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
        String isinToResolve = pickIsin(normalizedSymbol, isin);

        // ISIN always wins — even when symbol looks like a ticker (broker aliases).
        if (isinToResolve != null) {
            String resolved = lookupTradingSymbolByIsin(isinToResolve);
            if (resolved != null) {
                return resolved;
            }
            // ISIN miss: still try SYMBOL/NAME on the broker ticker before emitting ISIN-as-symbol.
            if (normalizedSymbol != null && !normalizedSymbol.isBlank()
                    && !TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
                String bySymbol = lookupTradingSymbolByQuery(normalizedSymbol, "SYMBOL");
                if (bySymbol != null && !bySymbol.equalsIgnoreCase(normalizedSymbol)) {
                    return bySymbol;
                }
                String byName = lookupTradingSymbolByQuery(normalizedSymbol, "NAME");
                if (byName != null && !byName.equalsIgnoreCase(normalizedSymbol)) {
                    return byName;
                }
            }
            return fallbackIdentifier(normalizedSymbol, isin);
        }

        // No ISIN (or ISIN miss above): canonicalize broker aliases via SYMBOL then NAME.
        // NAME matters when SYMBOL search returns the same alias (stale IDEA row) or misses.
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            String bySymbol = lookupTradingSymbolByQuery(normalizedSymbol, "SYMBOL");
            if (bySymbol != null && !bySymbol.equalsIgnoreCase(normalizedSymbol)) {
                return bySymbol;
            }
            String byName = lookupTradingSymbolByQuery(normalizedSymbol, "NAME");
            if (byName != null && !byName.equalsIgnoreCase(normalizedSymbol)) {
                return byName;
            }
            if (bySymbol != null) {
                return bySymbol;
            }
            return normalizedSymbol.trim().toUpperCase();
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
     * Point lookup: {@link MarketDataApiClient#resolveTickerByIsin} returns
     * {@code Map&lt;ISIN, tradingSymbol&gt;} (same shape as batch). Read by ISIN key —
     * do not look for a nested {@code "symbol"} property.
     */
    @SuppressWarnings("rawtypes")
    private String lookupTradingSymbolByIsin(String isin) {
        if (marketDataApiClient == null) {
            log.warn("MarketDataApiClient not available — skipping ISIN lookup for {}", isin);
            return null;
        }
        try {
            Map response = marketDataApiClient.resolveTickerByIsin(isin).block();
            return extractTicker(response, isin);
        } catch (Exception ex) {
            log.warn("ISIN lookup API call failed for {}: {}", isin, ex.getMessage());
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private String lookupTradingSymbolByQuery(String query, String searchField) {
        if (marketDataApiClient == null || query == null || query.isBlank()) {
            return null;
        }
        try {
            Map response = marketDataApiClient
                    .resolveTickersByQueries(List.of(query.trim().toUpperCase()), List.of(searchField))
                    .block();
            return extractTicker(response, query.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("SYMBOL/NAME lookup failed for {}: {}", query, ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private String extractTicker(Map response, String key) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        Object tickerObj = response.get(key);
        if (tickerObj == null) {
            for (Object k : response.keySet()) {
                if (k != null && key.equalsIgnoreCase(String.valueOf(k))) {
                    tickerObj = response.get(k);
                    break;
                }
            }
        }
        if (tickerObj == null) {
            return null;
        }
        String ticker = String.valueOf(tickerObj).trim().toUpperCase();
        if (ticker.isBlank() || TradingSymbolResolver.looksLikeIsin(ticker)) {
            log.warn("Lookup for {} returned non-ticker value: {}", key, ticker);
            return null;
        }
        return ticker;
    }

    /**
     * Resolves multiple ISIN codes to NSE/BSE symbols dynamically in a single batch API call.
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

            Map response = marketDataApiClient.resolveTickersByIsins(cleanedIsins).block();
            if (response != null) {
                Map<String, String> result = new java.util.HashMap<>();
                response.forEach((k, v) -> {
                    if (k != null && v != null) {
                        String ticker = String.valueOf(v).trim().toUpperCase();
                        if (!ticker.isBlank() && !TradingSymbolResolver.looksLikeIsin(ticker)) {
                            result.put(String.valueOf(k).trim().toUpperCase(), ticker);
                        }
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
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()
                && !TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            return normalizedSymbol.trim().toUpperCase();
        }
        return null;
    }
}
