package com.portfolio.model.cache;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis cache model for stock indices event data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIndicesEventDataCache {
    private String indexName;
    private String indexSymbol;
    private Double indexValue;
    private Double previousClose;
    private Double change;
    private Double changePercent;
    private Instant timestamp;
    private List<String> constituents;
}
