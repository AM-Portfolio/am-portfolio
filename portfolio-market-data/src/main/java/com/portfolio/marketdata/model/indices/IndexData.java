package com.portfolio.marketdata.model.indices;

import java.util.List;

import lombok.Data;

/**
 * Model class representing the NSE Index data response.
 */
@Data
public class IndexData {
    private String id;
    private String indexSymbol;
    private List<IndexConstituent> data;
    private IndexMetadata metadata;
    private String docVersion;
    private AuditInfo audit;
}
