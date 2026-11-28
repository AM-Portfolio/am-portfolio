package com.portfolio.model.portfolio;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human-readable MF labels from parser schemeName or broker CAS strings
 * ({@code AMC LTD#… MF-FUND NAME-DIRECT-GROWTH}).
 * <p>
 * Mirror of trade {@code SchemeMfDisplayNames} — keep in sync until a shared module exists.
 */
public final class SchemeMfDisplayNames {

    private static final Pattern AFTER_HASH = Pattern.compile("^[^#]*#\\s*(.+)$");
    private static final Pattern MF_PREFIX = Pattern.compile(
            "(?i)^(?:[A-Z0-9 .,&'-]+\\s+)?MF[-\\s]+(.+)$");

    private SchemeMfDisplayNames() {
    }

    /**
     * Prefer parser schemeName; else clean broker description; never return a bare INF* ISIN
     * when a better label exists.
     */
    public static String resolveDisplayName(String schemeName, String brokerDescription, String isinFallback) {
        String fromScheme = cleanLabel(schemeName);
        if (fromScheme != null) {
            return fromScheme;
        }
        String fromBroker = cleanBrokerDescription(brokerDescription);
        if (fromBroker != null) {
            return fromBroker;
        }
        if (isinFallback != null && !isinFallback.isBlank()) {
            return isinFallback.trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Short Symbol-column label (max ~40 chars) from a full scheme/broker name.
     */
    public static String resolveDisplaySymbol(String schemeName, String brokerDescription, String isinFallback) {
        String full = resolveDisplayName(schemeName, brokerDescription, null);
        if (full == null || full.isBlank()) {
            return isinFallback != null ? isinFallback.trim().toUpperCase(Locale.ROOT) : null;
        }
        String compact = full
                .replaceAll("(?i)\\bDIRECT\\b", " ")
                .replaceAll("(?i)\\bREGULAR\\b", " ")
                .replaceAll("(?i)\\bGROWTH\\b", " ")
                .replaceAll("(?i)\\bPLAN\\b", " ")
                .replaceAll("(?i)\\bOPTION\\b", " ")
                .replaceAll("\\s+-\\s+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (compact.length() > 40) {
            compact = compact.substring(0, 40).trim();
        }
        return compact.isEmpty() ? full : compact;
    }

    static String cleanBrokerDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        Matcher hash = AFTER_HASH.matcher(s);
        if (hash.matches()) {
            s = hash.group(1).trim();
        }
        Matcher mf = MF_PREFIX.matcher(s);
        if (mf.matches()) {
            s = mf.group(1).trim();
        }
        s = s.replaceAll("(?i)\\s*MF-\\s*", " ")
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return cleanLabel(s);
    }

    static String cleanLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.length() < 3) {
            return null;
        }
        // Reject bare ISIN as a "name".
        if (s.toUpperCase(Locale.ROOT).matches("INF[A-Z0-9]{9}")) {
            return null;
        }
        return s;
    }
}
