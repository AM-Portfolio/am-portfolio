package com.portfolio.model.util;

import java.util.Set;

public class SymbolResolver {

    private static final Set<String> KNOWN_EXCHANGE_SUFFIXES = 
        Set.of(".NS", ".BO", ".BSE", ".NFO", ".CDS");

    /**
     * Normalizes a market data symbol to its base ticker by stripping known exchange suffixes.
     * For example, "RELIANCE.NS" becomes "RELIANCE".
     *
     * @param rawSymbol the raw symbol from the market data feed
     * @return the normalized base ticker symbol
     */
    public static String normalize(String rawSymbol) {
        if (rawSymbol == null) {
            return null;
        }
        for (String suffix : KNOWN_EXCHANGE_SUFFIXES) {
            if (rawSymbol.endsWith(suffix)) {
                return rawSymbol.substring(0, rawSymbol.length() - suffix.length());
            }
        }
        return rawSymbol;
    }
}
