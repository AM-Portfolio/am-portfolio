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
        ALIASES.put("private sector bank", "financial services");
        ALIASES.put("public sector bank", "financial services");
        ALIASES.put("private bank", "financial services");
        ALIASES.put("public bank", "financial services");
        ALIASES.put("nbfc", "financial services");
        ALIASES.put("non banking financial companies", "financial services");
        ALIASES.put("mutual fund", "financial services");
        ALIASES.put("capital markets", "financial services");
        ALIASES.put("pharma", "pharmaceuticals");
        ALIASES.put("pharmaceuticals", "pharmaceuticals");
        ALIASES.put("pharmaceutical", "pharmaceuticals");
        ALIASES.put("healthcare", "healthcare");
        ALIASES.put("auto", "automobile");
        ALIASES.put("automobile", "automobile");
        ALIASES.put("fmcg", "consumer");
        ALIASES.put("computers - hardware", "information technology");
        ALIASES.put("computers hardware", "information technology");
        ALIASES.put("it - software", "information technology");
        ALIASES.put("diversified financials", "financial services");
        ALIASES.put("insurance", "financial services");
        ALIASES.put("oil gas & consumable fuels", "energy");
        ALIASES.put("oil & gas", "energy");
        ALIASES.put("energy", "energy");
        ALIASES.put("crude oil & natural gas", "energy");
        ALIASES.put("power", "energy");
        ALIASES.put("consumer durables", "consumer");
        ALIASES.put("consumer goods", "consumer");
        ALIASES.put("textiles", "textiles");
        ALIASES.put("construction", "infrastructure");
        ALIASES.put("infrastructure", "infrastructure");
        ALIASES.put("telecom", "communication");
        ALIASES.put("telecommunications", "communication");
        ALIASES.put("metals", "materials");
        ALIASES.put("metals & mining", "materials");
        ALIASES.put("chemicals", "materials");
        ALIASES.put("cement & cement products", "materials");
        ALIASES.put("real estate", "real estate");
        ALIASES.put("realty", "real estate");
        ALIASES.put("media & entertainment", "communication");
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
