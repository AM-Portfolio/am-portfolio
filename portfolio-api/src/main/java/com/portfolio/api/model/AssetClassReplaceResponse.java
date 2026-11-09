package com.portfolio.api.model;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of replacing one asset-class list")
public class AssetClassReplaceResponse {
    private UUID portfolioId;
    private String assetClass;
    private int itemCount;
    private Double totalValue;
}
