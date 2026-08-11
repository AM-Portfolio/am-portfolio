package com.am.common.amcommondata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reserves quantity from a BROKER portfolio into a BASKET portfolio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingAllocation {
    private String basketPortfolioId;
    private String isin;
    private String symbol;
    private Double quantity;
}
