package com.am.common.amcommondata.model.enums;

/**
 * Enum representing different types of mutual funds with their sub-categories.
 */
public enum FundType {
    // Equity Funds
    EQUITY_LARGE_CAP("Large Cap Equity Fund", "Equity"),
    EQUITY_MID_CAP("Mid Cap Equity Fund", "Equity"),
    EQUITY_SMALL_CAP("Small Cap Equity Fund", "Equity"),
    EQUITY_MULTI_CAP("Multi Cap Equity Fund", "Equity"),
    EQUITY_LARGE_MID_CAP("Large & Mid Cap Equity Fund", "Equity"),
    EQUITY_DIVIDEND_YIELD("Dividend Yield Fund", "Equity"),
    EQUITY_FOCUSED("Focused Equity Fund", "Equity"),
    EQUITY_CONTRA("Contra Fund", "Equity"),
    EQUITY_VALUE("Value Fund", "Equity"),

    // Debt Funds
    DEBT_ULTRA_SHORT("Ultra Short Duration Fund", "Debt"),
    DEBT_LOW_DURATION("Low Duration Fund", "Debt"),
    DEBT_SHORT_DURATION("Short Duration Fund", "Debt"),
    DEBT_MEDIUM_DURATION("Medium Duration Fund", "Debt"),
    DEBT_LONG_DURATION("Long Duration Fund", "Debt"),
    DEBT_DYNAMIC_BOND("Dynamic Bond Fund", "Debt"),
    DEBT_CORPORATE_BOND("Corporate Bond Fund", "Debt"),
    DEBT_CREDIT_RISK("Credit Risk Fund", "Debt"),
    DEBT_BANKING_PSU("Banking and PSU Fund", "Debt"),
    DEBT_FLOATER("Floater Fund", "Debt"),

    // Hybrid Funds
    HYBRID_AGGRESSIVE("Aggressive Hybrid Fund", "Hybrid"),
    HYBRID_BALANCED("Balanced Hybrid Fund", "Hybrid"),
    HYBRID_CONSERVATIVE("Conservative Hybrid Fund", "Hybrid"),
    HYBRID_EQUITY_SAVINGS("Equity Savings Fund", "Hybrid"),
    HYBRID_MULTI_ASSET("Multi Asset Allocation Fund", "Hybrid"),

    // Index Funds
    INDEX_NIFTY50("Nifty 50 Index Fund", "Index"),
    INDEX_SENSEX("Sensex Index Fund", "Index"),
    INDEX_NIFTY_NEXT50("Nifty Next 50 Index Fund", "Index"),
    INDEX_MIDCAP("Midcap Index Fund", "Index"),
    INDEX_SMALLCAP("Smallcap Index Fund", "Index"),
    INDEX_SECTORAL("Sectoral Index Fund", "Index"),

    // Sectoral/Thematic Funds
    SECTOR_BANKING("Banking Sector Fund", "Sectoral"),
    SECTOR_IT("IT Sector Fund", "Sectoral"),
    SECTOR_PHARMA("Pharma Sector Fund", "Sectoral"),
    SECTOR_FMCG("FMCG Sector Fund", "Sectoral"),
    SECTOR_INFRASTRUCTURE("Infrastructure Fund", "Sectoral"),
    SECTOR_AUTO("Auto Sector Fund", "Sectoral"),
    SECTOR_POWER("Power Sector Fund", "Sectoral"),
    SECTOR_METAL("Metal Sector Fund", "Sectoral"),
    SECTOR_CONSUMPTION("Consumption Sector Fund", "Sectoral"),
    
    THEME_ESG("ESG Fund", "Thematic"),
    THEME_MNC("MNC Fund", "Thematic"),
    THEME_GLOBAL_ADVANTAGE("Global Advantage Fund", "Thematic"),

    // Solution Oriented Funds
    SOLUTION_RETIREMENT("Retirement Fund", "Solution"),
    SOLUTION_CHILDREN("Children's Fund", "Solution"),
    SOLUTION_PENSION("Pension Fund", "Solution"),

    // International Funds
    INTL_US_EQUITY("US Equity Fund", "International"),
    INTL_EMERGING_MARKETS("Emerging Markets Fund", "International"),
    INTL_GLOBAL_EQUITY("Global Equity Fund", "International"),
    INTL_EUROPE("European Markets Fund", "International"),
    INTL_JAPAN("Japanese Markets Fund", "International"),
    INTL_CHINA("China Markets Fund", "International"),

    // Other Fund Types
    LIQUID_FUND("Liquid Fund", "Other"),
    OVERNIGHT_FUND("Overnight Fund", "Other"),
    ARBITRAGE_FUND("Arbitrage Fund", "Other"),
    GILT_FUND("Gilt Fund", "Other"),
    GILT_CONSTANT_MATURITY("Gilt with Constant Maturity", "Other"),
    ELSS_TAX_SAVER("ELSS Tax Saver", "Other"),
    FOF_DOMESTIC("Fund of Funds - Domestic", "Other"),
    FOF_OVERSEAS("Fund of Funds - Overseas", "Other"),

    //Default Type
    DEFAULT("Default", "Other");

    private final String displayName;
    private final String category;

    FundType(String displayName, String category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static FundType fromDisplayName(String displayName) {
        for (FundType type : values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        return DEFAULT;
    }

    // Helper methods for categories
    public boolean isEquity() {
        return category.equals("Equity");
    }

    public boolean isDebt() {
        return category.equals("Debt");
    }

    public boolean isHybrid() {
        return category.equals("Hybrid");
    }

    public boolean isIndex() {
        return category.equals("Index");
    }

    public boolean isSectoral() {
        return category.equals("Sectoral");
    }

    public boolean isThematic() {
        return category.equals("Thematic");
    }

    public boolean isSolution() {
        return category.equals("Solution");
    }

    public boolean isInternational() {
        return category.equals("International");
    }

    // Helper methods for specific fund types
    public boolean isLargeCap() {
        return this == EQUITY_LARGE_CAP;
    }

    public boolean isMidCap() {
        return this == EQUITY_MID_CAP;
    }

    public boolean isSmallCap() {
        return this == EQUITY_SMALL_CAP;
    }

    public boolean isELSS() {
        return this == ELSS_TAX_SAVER;
    }

    public boolean isLiquid() {
        return this == LIQUID_FUND;
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }
}