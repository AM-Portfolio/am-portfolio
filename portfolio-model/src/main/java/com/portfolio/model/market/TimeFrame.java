package com.portfolio.model.market;

/**
 * Enum representing different time intervals for market data.
 */
public enum TimeFrame {
    MINUTE("minute"),
    FIVE_MIN("5min"),
    TEN_MIN("10min"),
    FIFTEEN_MIN("15mins"),
    THIRTY_MIN("30min"),
    HOUR("hour"),
    DAY("day");
    
    private final String value;
    
    TimeFrame(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get TimeFrame from string value.
     * 
     * @param value the string value
     * @return the corresponding TimeFrame or null if not found
     */
    public static TimeFrame fromValue(String value) {
        for (TimeFrame timeFrame : TimeFrame.values()) {
            if (timeFrame.getValue().equals(value)) {
                return timeFrame;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
