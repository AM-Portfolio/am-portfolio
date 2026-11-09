package com.portfolio.model.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds Upstox / am-market instrument keys without hardcoding the cash-equity segment in callers.
 * Default cash prefix is {@code NSE_EQ}; override via {@code market-data.api.equity-instrument-prefix}.
 */
public final class InstrumentKeys {

    public static final String DEFAULT_EQUITY_PREFIX = "NSE_EQ";

    /** Segments that already identify an instrument and must not be rewritten to cash equity. */
    public static final Set<String> DEFAULT_PASSTHROUGH_PREFIXES = Set.of(
            "NSE_FO", "NSE_INDEX", "BSE_EQ", "BSE_FO", "BSE_INDEX", "MCX_FO");

    private InstrumentKeys() {}

    /**
     * Normalize a bare ticker or exchange-prefixed symbol into a market-data instrument key.
     * Already-qualified segment keys ({@code NSE_FO:…}, etc.) are left alone.
     */
    public static String toInstrumentKey(String raw, String equityPrefix) {
        return toInstrumentKey(raw, equityPrefix, DEFAULT_PASSTHROUGH_PREFIXES);
    }

    public static String toInstrumentKey(String raw, String equityPrefix, Set<String> passthroughPrefixes) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = raw.trim();
        String cash = normalizePrefix(equityPrefix);
        Set<String> pass = (passthroughPrefixes == null || passthroughPrefixes.isEmpty())
                ? DEFAULT_PASSTHROUGH_PREFIXES
                : passthroughPrefixes;

        int cut = segmentSeparatorIndex(s);
        if (cut > 0) {
            String head = s.substring(0, cut).toUpperCase(Locale.ROOT);
            String tail = s.substring(cut + 1).trim();
            if (tail.isEmpty()) {
                return s;
            }
            if (pass.contains(head) || cash.equalsIgnoreCase(head)) {
                return head + ":" + tail;
            }
            // Legacy bare exchange: NSE:RELIANCE → NSE_EQ:RELIANCE (configured cash prefix).
            // BSE: stays on BSE cash segment — never rewrite to NSE_EQ (would break BSE-only names).
            if ("NSE".equals(head)) {
                return cash + ":" + SymbolResolver.normalize(tail);
            }
            if ("BSE".equals(head)) {
                return "BSE_EQ:" + SymbolResolver.normalize(tail);
            }
            return s;
        }

        String ticker = SymbolResolver.normalize(s);
        if (ticker == null || ticker.isBlank()) {
            return cash + ":" + s.toUpperCase(Locale.ROOT);
        }
        return cash + ":" + ticker;
    }

    /** {@code NSE_EQ:PWL} style key for a normalized ticker. */
    public static String equityKey(String ticker, String equityPrefix) {
        String t = ticker == null ? "" : ticker.trim();
        if (t.isEmpty()) {
            return t;
        }
        return normalizePrefix(equityPrefix) + ":" + t;
    }

    /** Colon or Dhan-style pipe: {@code NSE_EQ|RELIANCE} → same as {@code NSE_EQ:RELIANCE}. */
    static int segmentSeparatorIndex(String s) {
        int colon = s.indexOf(':');
        int pipe = s.indexOf('|');
        if (colon > 0 && (pipe < 0 || colon < pipe)) {
            return colon;
        }
        return pipe > 0 ? pipe : -1;
    }

    public static String normalizePrefix(String equityPrefix) {
        if (equityPrefix == null || equityPrefix.isBlank()) {
            return DEFAULT_EQUITY_PREFIX;
        }
        String p = equityPrefix.trim();
        if (p.endsWith(":")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.toUpperCase(Locale.ROOT);
    }

    public static Set<String> parsePassthroughPrefixes(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_PASSTHROUGH_PREFIXES;
        }
        Set<String> out = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.endsWith(":") ? s.substring(0, s.length() - 1) : s)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return out.isEmpty() ? DEFAULT_PASSTHROUGH_PREFIXES : Collections.unmodifiableSet(out);
    }
}
