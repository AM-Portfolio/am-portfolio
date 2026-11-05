package com.portfolio.service.resolver;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.model.portfolio.HoldingInstrumentClassifier;
import com.portfolio.model.resolver.TradingSymbolResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalizes equity symbols on any inbound portfolio payload before Mongo persist.
 *
 * <p>Industry-grade rule: the trading {@code symbol} must come from market-data
 * (ISIN → ticker, or COMPANY_NAME / SYMBOL search). Never invent tickers by
 * compacting broker display names ({@code PHYSICSWALLAH-EQ} → {@code PHYSICSWALLAH});
 * those break LTP. When ISIN is present and MD has no hit yet, keep/restore the
 * ISIN so heal can upgrade later for any future upload.
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
            return e.getIsin().trim().toUpperCase(Locale.ROOT);
        }
        if (TradingSymbolResolver.looksLikeIsin(e.getSymbol())) {
            return e.getSymbol().trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private void applyResolvedSymbol(EquityModel equity, Map<String, String> resolvedByIsin) {
        String isinKey = extractIsin(equity);

        if ((equity.getIsin() == null || equity.getIsin().isBlank())
                && TradingSymbolResolver.looksLikeIsin(equity.getSymbol())) {
            equity.setIsin(equity.getSymbol().trim().toUpperCase(Locale.ROOT));
            isinKey = equity.getIsin();
        }

        // 1) Authoritative: market-data ISIN → NSE ticker (PWL, VIKRAMSOLR, TATAGOLD, …).
        if (isinKey != null && resolvedByIsin.containsKey(isinKey)) {
            if (applyTicker(equity, resolvedByIsin.get(isinKey), isinKey)) {
                return;
            }
        }

        // 2) COMPANY_NAME via market-data only (never invented compact names).
        if (equity.getName() != null && !equity.getName().isBlank()) {
            String byName = lookupByName(equity.getName());
            if (byName != null
                    && !HoldingInstrumentClassifier.isUnsafeNameToTickerMap(equity.getName(), byName)
                    && applyTicker(equity, byName, isinKey)) {
                return;
            }
        }

        // 3) No ISIN: SYMBOL search for broker aliases (IDEA → canonical ticker).
        if (isinKey == null) {
            String bySymbol = tradingSymbolResolver.resolveTradingSymbol(equity.getSymbol(), null);
            if (bySymbol != null
                    && !TradingSymbolResolver.looksLikeIsin(bySymbol)
                    && applyTicker(equity, bySymbol, null)) {
                return;
            }
            return;
        }

        // 4) ISIN present but MD miss: undo invented display tickers so heal retries.
        revertInventedDisplayTickerToIsin(equity, isinKey);
    }

    /**
     * @return true when symbol was set to a non-ISIN market ticker
     */
    private boolean applyTicker(EquityModel equity, String ticker, String isinKey) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }
        String clean = ticker.trim().toUpperCase(Locale.ROOT);
        if (TradingSymbolResolver.looksLikeIsin(clean)) {
            return false;
        }
        if (HoldingInstrumentClassifier.isUnsafeNameToTickerMap(equity.getName(), clean)) {
            log.warn("Rejected unsafe resolved ticker name={} ticker={}", equity.getName(), clean);
            return false;
        }
        String before = equity.getSymbol();
        equity.setSymbol(clean);
        if (before == null || !before.equalsIgnoreCase(clean)) {
            log.info("Normalized equity symbol {} → {} (isin={})", before, clean, isinKey);
        }
        return true;
    }

    /**
     * Clears broker-name compact symbols (PHYSICSWALLAH, VIKRAMSOLAR, …) back to ISIN
     * so read-path heal can apply a real MD ticker once instruments exist.
     * Leaves real tickers (IDEA, PWL, …) untouched.
     */
    private void revertInventedDisplayTickerToIsin(EquityModel equity, String isinKey) {
        if (isinKey == null || equity.getName() == null || equity.getName().isBlank()) {
            return;
        }
        String sym = equity.getSymbol();
        if (sym == null || sym.isBlank() || TradingSymbolResolver.looksLikeIsin(sym)) {
            return;
        }
        String invented = HoldingInstrumentClassifier.suggestDisplayTickerFromName(equity.getName());
        if (invented == null || !invented.equalsIgnoreCase(sym.trim())) {
            return;
        }
        equity.setSymbol(isinKey);
        log.info("Reverted invented display ticker {} → ISIN {} until market-data resolves",
                invented, isinKey);
    }

    @SuppressWarnings("rawtypes")
    private String lookupByName(String name) {
        if (marketDataApiClient == null || name == null || name.isBlank()) {
            return null;
        }
        try {
            String query = HoldingInstrumentClassifier.stripBrokerSeriesSuffix(name.trim());
            if (query == null || query.isBlank()) {
                return null;
            }
            Map response = marketDataApiClient
                    .resolveTickersByQueries(List.of(query), List.of("COMPANY_NAME"))
                    .block();
            if (response == null || response.isEmpty()) {
                return null;
            }
            Object ticker = response.get(query.toUpperCase(Locale.ROOT));
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
            String t = String.valueOf(ticker).trim().toUpperCase(Locale.ROOT);
            if (t.isBlank() || TradingSymbolResolver.looksLikeIsin(t)) {
                return null;
            }
            return t;
        } catch (Exception e) {
            log.warn("COMPANY_NAME lookup failed for {}: {}", name, e.getMessage());
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
