package com.portfolio.marketdata.model.indices;

import lombok.Data;

/**
 * Model class representing a constituent stock in an index.
 */
@Data
public class IndexConstituent {
    private String symbol;
    private String identifier;
    private String series;
    private String name;
    private String companyName;
    private String isin;
    private String industry;
}
