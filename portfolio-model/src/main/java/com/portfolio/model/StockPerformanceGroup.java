package com.portfolio.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockPerformanceGroup {
    private List<StockPerformance> topPerformers; // Default top 5
    private PaginatedStockPerformance allPerformers; // Paginated full list
    private double averagePerformance;
    private double medianPerformance;
    private double bestPerformance;
    private double worstPerformance;
    private int totalCount;
}
