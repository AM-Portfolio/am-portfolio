package com.portfolio.basket.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes sector labels so ETF vs user holdings can match.
 */
public final class SectorNormalizer {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("it", "information technology");
        ALIASES.put("it services", "information technology");
        ALIASES.put("information technology", "information technology");
        ALIASES.put("computers - software & consulting", "information technology");
        ALIASES.put("computers - software and consulting", "information technology");
        ALIASES.put("computers software", "information technology");
        ALIASES.put("software", "information technology");
        ALIASES.put("software products", "information technology");
        ALIASES.put("software product", "information technology");
        ALIASES.put("technology", "information technology");
        ALIASES.put("tech", "information technology");
        ALIASES.put("banks", "financial services");
        ALIASES.put("bank", "financial services");
        ALIASES.put("finance", "financial services");
        ALIASES.put("financial", "financial services");
        ALIASES.put("financial services", "financial services");
        ALIASES.put("pharma", "pharmaceuticals");
        ALIASES.put("pharmaceuticals", "pharmaceuticals");
        ALIASES.put("healthcare", "healthcare");
        ALIASES.put("auto", "automobile");
        ALIASES.put("automobile", "automobile");
        ALIASES.put("fmcg", "fmcg");
        ALIASES.put("unknown", "unknown");
    }

    private SectorNormalizer() {
    }

    public static String normalize(String sector) {
        if (sector == null || sector.isBlank()) {
            return "unknown";
        }
        String key = sector.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return ALIASES.getOrDefault(key, key);
    }

    public static boolean isUnknown(String sector) {
        String n = normalize(sector);
        return "unknown".equals(n) || n.isBlank();
    }
}
