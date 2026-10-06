package com.portfolio.service.resolver;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.model.resolver.TradingSymbolResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes equity symbols on any inbound portfolio payload before Mongo persist.
 *
 * <p>ISIN is always preferred when present — broker tickers that only look like symbols
 * (IDEA, VIKRAMSOLR, …) are re-resolved via market-data. When ISIN is blank, SYMBOL then
 * NAME batch-search is used so future uploads still get a canonical NSE ticker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioEquitySymbolNormalizer {

    private final TradingSymbolResolver tradingSymbolResolver;
    private final MarketDataApiClient marketDataApiClient;

    public void normalizePortfolio(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return;
        }
        normalizeEquities(portfolio.getEquityModels());
    }

    public void normalizeEquities(List<EquityModel> equities) {
        if (equities == null || equities.isEmpty()) {
            return;
        }

        List<String> isinsToResolve = equities.stream()
                .filter(e -> e != null)
                .map(this::extractIsin)
                .filter(isin -> isin != null && !isin.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> resolvedByIsin = Map.of();
        if (!isinsToResolve.isEmpty()) {
            resolvedByIsin = tradingSymbolResolver.resolveTradingSymbols(isinsToResolve);
        }

        for (EquityModel equity : equities) {
            if (equity == null) {
                continue;
            }
            applyResolvedSymbol(equity, resolvedByIsin);
        }
    }

    private String extractIsin(EquityModel e) {
        if (e.getIsin() != null && !e.getIsin().isBlank()) {
            return e.getIsin().trim().toUpperCase();
        }
        if (TradingSymbolResolver.looksLikeIsin(e.getSymbol())) {
            return e.getSymbol().trim().toUpperCase();
        }
        return null;
    }

    private void applyResolvedSymbol(EquityModel equity, Map<String, String> resolvedByIsin) {
        String isinKey = extractIsin(equity);

        if ((equity.getIsin() == null || equity.getIsin().isBlank())
                && TradingSymbolResolver.looksLikeIsin(equity.getSymbol())) {
            equity.setIsin(equity.getSymbol().trim().toUpperCase());
            isinKey = equity.getIsin();
        }

        String resolved = null;
        if (isinKey != null && resolvedByIsin.containsKey(isinKey)) {
            resolved = resolvedByIsin.get(isinKey);
        }

        if (resolved == null) {
            resolved = tradingSymbolResolver.resolveTradingSymbol(equity.getSymbol(), equity.getIsin());
        }

        String before = equity.getSymbol();
        boolean unchangedAlias = resolved != null && before != null && resolved.equalsIgnoreCase(before);

        // NAME search when unresolved, ISIN-shaped, or broker alias unchanged (IDEA→IDEA).
        if ((resolved == null || resolved.isBlank() || TradingSymbolResolver.looksLikeIsin(resolved) || unchangedAlias)
                && equity.getName() != null && !equity.getName().isBlank()) {
            String byName = lookupByName(equity.getName());
            if (byName != null && (before == null || !byName.equalsIgnoreCase(before))) {
                resolved = byName;
            }
        }

        if (resolved == null || resolved.isBlank() || TradingSymbolResolver.looksLikeIsin(resolved)) {
            return;
        }

        equity.setSymbol(resolved);
        if (before != null && !before.equalsIgnoreCase(resolved)) {
            log.info("Normalized equity symbol {} → {} (isin={})", before, resolved, isinKey);
        }
    }

    @SuppressWarnings("rawtypes")
    private String lookupByName(String name) {
        if (marketDataApiClient == null || name == null || name.isBlank()) {
            return null;
        }
        try {
            String query = name.trim();
            Map response = marketDataApiClient
                    .resolveTickersByQueries(List.of(query), List.of("NAME"))
                    .block();
            if (response == null || response.isEmpty()) {
                return null;
            }
            Object ticker = response.get(query.toUpperCase());
            if (ticker == null) {
                for (Object k : response.keySet()) {
                    if (k != null && query.equalsIgnoreCase(String.valueOf(k))) {
                        ticker = response.get(k);
                        break;
                    }
                }
            }
            if (ticker == null) {
                return null;
            }
            String t = String.valueOf(ticker).trim().toUpperCase();
            if (t.isBlank() || TradingSymbolResolver.looksLikeIsin(t)) {
                return null;
            }
            return t;
        } catch (Exception e) {
            log.warn("NAME lookup failed for {}: {}", name, e.getMessage());
            return null;
        }
    }

    /** True when any equity symbol changed after normalization (repair endpoints). */
    public boolean normalizePortfolioAndDetectChange(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return false;
        }
        Map<Integer, String> before = new HashMap<>();
        List<EquityModel> equities = portfolio.getEquityModels();
        for (int i = 0; i < equities.size(); i++) {
            EquityModel e = equities.get(i);
            before.put(i, e != null ? e.getSymbol() : null);
        }
        normalizeEquities(equities);
        for (int i = 0; i < equities.size(); i++) {
            EquityModel e = equities.get(i);
            String after = e != null ? e.getSymbol() : null;
            String b = before.get(i);
            if (b == null ? after != null : !b.equals(after)) {
                return true;
            }
        }
        return false;
    }
}
