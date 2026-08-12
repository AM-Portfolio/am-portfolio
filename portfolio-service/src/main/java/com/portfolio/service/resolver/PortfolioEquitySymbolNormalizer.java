package com.portfolio.service.resolver;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.resolver.TradingSymbolResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Normalizes equity symbols on any inbound portfolio payload before Mongo persist.
 *
 * <p>Covers HTTP sync from trade-management which bypasses {@link com.portfolio.model.mapper.PortfolioMapperv1}.
 */
@Service
@RequiredArgsConstructor
public class PortfolioEquitySymbolNormalizer {

    private final TradingSymbolResolver tradingSymbolResolver;

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

        // Gather all unique ISIN codes from the equities to perform a single batch lookup
        java.util.List<String> isinsToResolve = equities.stream()
                .filter(e -> e != null)
                .map(e -> {
                    String normalized = e.getSymbol() != null ? com.portfolio.model.util.SymbolResolver.normalize(e.getSymbol()) : null;
                    if (normalized != null && !normalized.isBlank() && !TradingSymbolResolver.looksLikeIsin(normalized)) {
                        return null; // Already a standard ticker symbol, no need to resolve
                    }
                    if (e.getIsin() != null && !e.getIsin().isBlank()) {
                        return e.getIsin().trim().toUpperCase();
                    }
                    if (TradingSymbolResolver.looksLikeIsin(e.getSymbol())) {
                        return e.getSymbol().trim().toUpperCase();
                    }
                    return null;
                })
                .filter(isin -> isin != null && !isin.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        // Perform batch call to fetch resolved symbols
        java.util.Map<String, String> resolvedMap = java.util.Map.of();
        if (!isinsToResolve.isEmpty()) {
            resolvedMap = tradingSymbolResolver.resolveTradingSymbols(isinsToResolve);
        }

        for (EquityModel equity : equities) {
            if (equity == null) {
                continue;
            }
            applyResolvedSymbol(equity, resolvedMap);
        }
    }

    private void applyResolvedSymbol(EquityModel equity, java.util.Map<String, String> resolvedMap) {
        // Resolve using the batch map if present; otherwise fall back to point lookup resolver
        String isinKey = equity.getIsin() != null && !equity.getIsin().isBlank() 
                ? equity.getIsin().trim().toUpperCase() 
                : (TradingSymbolResolver.looksLikeIsin(equity.getSymbol()) ? equity.getSymbol().trim().toUpperCase() : null);

        String resolved = null;
        if (isinKey != null && resolvedMap.containsKey(isinKey)) {
            resolved = resolvedMap.get(isinKey);
        }

        if (resolved == null) {
            resolved = tradingSymbolResolver.resolveTradingSymbol(equity.getSymbol(), equity.getIsin());
        }

        if (resolved == null || resolved.isBlank()) {
            return;
        }

        // Keep ISIN in its own field; symbol should be the tradable ticker when possible.
        if (TradingSymbolResolver.looksLikeIsin(equity.getIsin())
                || (equity.getIsin() == null && TradingSymbolResolver.looksLikeIsin(equity.getSymbol()))) {
            if (equity.getIsin() == null || equity.getIsin().isBlank()) {
                equity.setIsin(TradingSymbolResolver.looksLikeIsin(equity.getSymbol())
                        ? equity.getSymbol().trim().toUpperCase()
                        : equity.getIsin());
            }
        }

        if (!TradingSymbolResolver.looksLikeIsin(resolved)) {
            equity.setSymbol(resolved);
        }
    }
}

