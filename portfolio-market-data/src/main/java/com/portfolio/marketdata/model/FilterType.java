package com.portfolio.marketdata.model;

/**
 * Enum representing different types of data filtering.
 */
public enum FilterType {
    ALL("ALL"),
    START_END("START_END"),
    CUSTOM("CUSTOM");
    
    private final String value;
    
    FilterType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get FilterType from string value.
     * 
     * @param value the string value
     * @return the corresponding FilterType or null if not found
     */
    public static FilterType fromValue(String value) {
        for (FilterType type : FilterType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
