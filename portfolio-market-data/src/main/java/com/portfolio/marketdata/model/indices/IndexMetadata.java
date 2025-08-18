package com.portfolio.marketdata.model.indices;

import lombok.Data;

/**
 * Model class representing metadata for an index.
 */
@Data
public class IndexMetadata {
    private String indexName;
    private double open;
    private double high;
    private double low;
    private double previousClose;
    private double percChange;
    private double change;
    private long totalTradedVolume;
    private double ffmc_sum;
}
