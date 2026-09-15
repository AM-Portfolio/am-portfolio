package com.portfolio.model.market;

import java.util.Locale;

/**
 * Enum representing different time intervals for market data.
 */
public enum TimeFrame {
    MINUTE("1m"),
    THREE_MIN("3m"),
    FIVE_MIN("5m"),
    TEN_MIN("10m"),
    FIFTEEN_MIN("15m"),
    THIRTY_MIN("30m"),
    HOUR("1H"),
    FOUR_HOUR("4H"),
    DAY("1D"),
    WEEK("1W"),
    MONTH("1M"),
    YEAR("1Y");
    
    private final String value;
    
    TimeFrame(String value) {
        this.value = value;
    }
    
    @com.fasterxml.jackson.annotation.JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * Get TimeFrame from string value.
     * 
     * @param value the string value
     * @return the corresponding TimeFrame or null if not found
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static TimeFrame fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        // Prefer exact @JsonValue match first (1M vs 1m must not collide).
        for (TimeFrame timeFrame : TimeFrame.values()) {
            if (timeFrame.getValue().equals(normalized)) {
                return timeFrame;
            }
        }
        // Legacy FE / enum-name aliases
        switch (normalized.toUpperCase(Locale.ROOT)) {
            case "DAY":
            case "ONEDAY":
            case "ONE_DAY":
                return DAY;
            case "WEEK":
            case "ONEWEEK":
            case "ONE_WEEK":
                return WEEK;
            case "MONTH":
            case "ONEMONTH":
            case "ONE_MONTH":
                return MONTH;
            case "YEAR":
            case "ONEYEAR":
            case "ONE_YEAR":
                return YEAR;
            default:
                // Case-insensitive fallback for codes that are unique ignoring case
                for (TimeFrame timeFrame : TimeFrame.values()) {
                    if (timeFrame.getValue().equalsIgnoreCase(normalized)
                            && !timeFrame.getValue().equalsIgnoreCase("1m")
                            && !timeFrame.getValue().equalsIgnoreCase("1M")) {
                        return timeFrame;
                    }
                }
                if ("1D".equalsIgnoreCase(normalized)) {
                    return DAY;
                }
                if ("1W".equalsIgnoreCase(normalized)) {
                    return WEEK;
                }
                if ("1Y".equalsIgnoreCase(normalized)) {
                    return YEAR;
                }
                return null;
        }
    }
    
    @Override
    public String toString() {
        return value;
    }
}
