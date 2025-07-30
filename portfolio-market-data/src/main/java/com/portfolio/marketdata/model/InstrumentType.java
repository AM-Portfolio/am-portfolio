package com.portfolio.marketdata.model;

/**
 * Enum representing different types of financial instruments.
 */
public enum InstrumentType {
    STOCK("STOCK"),
    OPTION("OPTION"),
    MUTUAL_FUND("MUTUAL_FUND"),
    EQ("EQ"),
    INDEX("INDEX");
    
    private final String value;
    
    InstrumentType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Get InstrumentType from string value.
     * 
     * @param value the string value
     * @return the corresponding InstrumentType or null if not found
     */
    public static InstrumentType fromValue(String value) {
        for (InstrumentType type : InstrumentType.values()) {
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
