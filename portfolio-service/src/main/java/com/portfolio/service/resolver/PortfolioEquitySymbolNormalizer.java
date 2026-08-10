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
        if (equities == null) {
            return;
        }
        for (EquityModel equity : equities) {
            if (equity == null) {
                continue;
            }
            applyResolvedSymbol(equity);
        }
    }

    private void applyResolvedSymbol(EquityModel equity) {
        String resolved = tradingSymbolResolver.resolveTradingSymbol(equity.getSymbol(), equity.getIsin());
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
