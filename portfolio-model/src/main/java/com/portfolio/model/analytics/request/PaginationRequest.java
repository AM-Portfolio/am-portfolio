package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common request parameters for paginated analytics results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

    /**
     * Page number (0-based)
     */
    @Builder.Default
    private int page = 0;
    
    /**
     * Number of items per page
     */
    @Builder.Default
    private int size = 20;
    
    /**
     * Sort field
     */
    private String sortBy;
    
    /**
     * Sort direction (asc/desc)
     */
    @Builder.Default
    private String sortDirection = "desc";
    
    /**
     * Whether to return all data at once (ignores page and size if true)
     */
    @Builder.Default
    private boolean returnAllData = false;
}
