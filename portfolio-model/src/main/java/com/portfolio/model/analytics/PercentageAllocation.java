package com.portfolio.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents percentage allocation of a portfolio by industry, sector, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PercentageAllocation {
    private String industry;
    private double allocation;
}
