package com.portfolio.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Basic portfolio information containing only essential details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Basic portfolio information with ID and name")
public class PortfolioBasicInfo {
    
    @Schema(description = "Unique portfolio identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private String portfolioId;
    
    @Schema(description = "Portfolio name", example = "My Investment Portfolio")
    private String portfolioName;
}