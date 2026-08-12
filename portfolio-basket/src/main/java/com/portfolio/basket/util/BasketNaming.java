package com.portfolio.basket.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives short basket names from ETF titles.
 */
public final class BasketNaming {

    private static final Pattern PREFIX = Pattern.compile(
            "(?i)^(nippon india etf|uti|hdfc|icici prudential|sbi|kotak|mirae asset|motilal oswal|axis|aditya birla sun life|dsp|invesco|tata|edelweiss|navi|groww)\\s+");

    private static final Pattern SUFFIX = Pattern.compile(
            "(?i)\\s*(bees|etf|fund|scheme|plan|growth|direct|regular|idcw)\\s*$");

    private BasketNaming() {
    }

    public static String shorten(String etfName) {
        if (etfName == null || etfName.isBlank()) {
            return "Basket";
        }
        String name = etfName.trim();
        name = PREFIX.matcher(name).replaceFirst("");
        name = SUFFIX.matcher(name).replaceFirst("");
        name = name.replaceAll("(?i)\\s*ETF\\s*", " ").trim();
        name = name.replaceAll("\\s+", " ").trim();
        if (name.isBlank()) {
            return etfName.trim();
        }
        return name;
    }

    public static String defaultBasketName(String etfName, String sourcePortfolioName) {
        String shortEtf = shorten(etfName);
        String source = sourcePortfolioName != null && !sourcePortfolioName.isBlank()
                ? sourcePortfolioName.trim()
                : "Portfolio";
        return shortEtf + " · " + source;
    }
}
