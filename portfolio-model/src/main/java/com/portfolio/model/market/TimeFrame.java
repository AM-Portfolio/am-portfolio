package com.portfolio.model.market;

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
