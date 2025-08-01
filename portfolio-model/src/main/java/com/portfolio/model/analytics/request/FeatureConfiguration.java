package com.portfolio.model.analytics.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeatureConfiguration {
    /**
     * Number of top movers to return (default will be applied if not specified)
     */
    @Min(value = 1, message = "Movers limit must be at least 1")
    private Integer moversLimit;
}