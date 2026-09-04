package com.portfolio.service.basket.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BasketLineDetailDto {
    private String symbol;
    private String isin;
    private String sector;
    private String status;
    private Double quantity;
    private Double avgPrice;
    private Double currentPrice;
    private Double pnl;
    private Double etfWeight;
    private Double rebalancedWeight;
    private String companyName;
    private String coversEtfSymbol;
    private Boolean stalePrice;
}
