package com.portfolio.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaginatedStockPerformance {
    private List<StockPerformance> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean isLastPage;
}
