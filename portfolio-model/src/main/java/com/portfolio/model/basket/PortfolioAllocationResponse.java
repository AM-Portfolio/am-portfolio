package com.portfolio.model.basket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Comprehensive portfolio allocation response showing stocks, sectors,
 * direct/indirect breakdowns
 * Designed for visualization in UI with graphics/charts
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortfolioAllocationResponse {
    private String userId;
    private String portfolioId;

    // Overview stats
    private AllocationOverview overview;

    // Stock-wise allocation
    private List<StockAllocation> stockAllocations;

    // Sector-wise allocation
    private List<SectorAllocation> sectorAllocations;

    // Direct vs Indirect breakdown
    private DirectIndirectBreakdown directIndirectBreakdown;

    /**
     * High-level overview statistics
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationOverview {
        private int totalStocks;
        private double totalDirectPercentage;
        private double totalIndirectPercentage;
        private int directStockCount;
        private int indirectStockCount;
        private int totalSectors;
    }

    /**
     * Stock-wise allocation with source breakdown
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockAllocation {
        private String isin;
        private String symbol;
        private String name;
        private String sector;
        private double totalPercentage;
        private double directPercentage;
        private double indirectPercentage;
        private List<AllocationSource> sources;
    }

    /**
     * Sector-wise allocation with direct/indirect breakdown
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorAllocation {
        private String sectorName;
        private double totalPercentage;
        private double directPercentage;
        private double indirectPercentage;
        private int stockCount;
        private List<String> topStocks; // Top 3-5 stocks in this sector
    }

    /**
     * Source of allocation (portfolio, ETF, mutual fund, etc.)
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationSource {
        private SourceType sourceType; // DIRECT_PORTFOLIO, ETF, MUTUAL_FUND
        private String sourceId; // Portfolio ID, ETF ISIN, Fund code
        private String sourceName; // Portfolio name, ETF name, Fund name
        private double contribution; // Percentage contribution
    }

    /**
     * High-level direct vs indirect breakdown
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DirectIndirectBreakdown {
        private DirectAllocation directAllocation;
        private List<IndirectAllocation> indirectAllocations;
    }

    /**
     * Direct allocation from portfolios
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DirectAllocation {
        private double totalPercentage;
        private int stockCount;
        private List<PortfolioContribution> portfolioContributions;
    }

    /**
     * Portfolio contribution to direct holdings
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PortfolioContribution {
        private String portfolioId;
        private String portfolioName;
        private double percentage;
        private int stockCount;
    }

    /**
     * Indirect allocation from ETFs, Mutual Funds, etc.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndirectAllocation {
        private SourceType sourceType; // ETF or MUTUAL_FUND
        private String sourceId;
        private String sourceName;
        private double percentage;
        private int stockCount;
    }

    /**
     * Type of allocation source
     */
    public enum SourceType {
        DIRECT_PORTFOLIO, // Direct holdings in user's portfolio
        ETF, // Indirect via ETF
        MUTUAL_FUND // Indirect via Mutual Fund
    }
}
