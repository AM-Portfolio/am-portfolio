package com.portfolio.model.util;

import java.util.Set;

public class SymbolResolver {

    private static final Set<String> KNOWN_EXCHANGE_SUFFIXES = 
        Set.of(
            // Groww, ICICI
            ".NS", ".BO", ".BSE", ".NFO", ".CDS",
            // Zerodha, AngelOne, Dhan, Paytm
            "-EQ", "-BE", "-SM", "-ST", "-BZ", "-RR", "-Z",
            // ICICI Direct, AngelOne alternate
            " EQ", " BE", "_EQ", "_BE"
        );

    private static final Set<String> KNOWN_EXCHANGE_PREFIXES = 
        Set.of(
            "NSE:", "BSE:", "NFO:", "CDS:", 
            "NSE_EQ|", "BSE_EQ|", "NSE_EQ:", "BSE_EQ:",
            "NSE_", "BSE_"
        );

    /**
     * Normalizes a market data symbol to its base ticker by stripping known exchange prefixes and suffixes.
     * For example, "NSE:RELIANCE-EQ" or "NSE_EQ|RELIANCE" becomes "RELIANCE".
     *
     * @param rawSymbol the raw symbol from the market data feed or broker
     * @return the normalized base ticker symbol
     */
    public static String normalize(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.trim().isEmpty()) {
            return rawSymbol;
        }

        String cleaned = rawSymbol.trim().toUpperCase();

        // 1. Strip ISIN (INE...) as it can't be easily resolved to a short ticker here
        // Usually, if it's an ISIN, we might need a DB lookup. But for now, if it contains an ISIN, 
        // we extract the part after the separator if available, or just leave it.
        // E.g., NSE_EQ|INE467B01029 -> INE467B01029. 
        // We will just strip prefixes and suffixes first.

        // 2. Strip Prefixes
        boolean prefixStripped;
        do {
            prefixStripped = false;
            for (String prefix : KNOWN_EXCHANGE_PREFIXES) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.substring(prefix.length());
                    prefixStripped = true;
                    break;
                }
            }
        } while (prefixStripped);

        // General colon strip if it's still there (e.g., "NSE:TCS")
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex > 0 && colonIndex < cleaned.length() - 1) {
            cleaned = cleaned.substring(colonIndex + 1);
        }

        // 3. Strip Suffixes
        boolean suffixStripped;
        do {
            suffixStripped = false;
            for (String suffix : KNOWN_EXCHANGE_SUFFIXES) {
                if (cleaned.endsWith(suffix)) {
                    cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
                    suffixStripped = true;
                    break;
                }
            }
        } while (suffixStripped);

        return cleaned;
    }
}
