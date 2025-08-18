package com.portfolio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds total values for sector and symbol allocations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalValue {
    private double totalAllocationPercentage;
    private double totalSymbolPercentage;
}
