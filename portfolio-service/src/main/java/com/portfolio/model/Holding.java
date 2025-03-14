package com.portfolio.model;

import com.portfolio.model.enums.AssetType;
import com.portfolio.model.enums.BrokerType;
import com.portfolio.model.enums.PlatformType;
import com.portfolio.model.MarketBreakdown;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class Holding {
    private String assetId;
    private String symbol;
    private String assetName;
    private AssetType assetType;
    private BigDecimal quantity;
    private BigDecimal purchasePrice;
    private LocalDate purchaseDate;
    private BrokerType brokerType;
    private PlatformType platformType;
    private BigDecimal currentValue;
    private BigDecimal unrealizedGainLoss;
    private double percentageGainLoss;
    
    // Additional fields for sector and market analysis
    private String sector;
    private String industry;
    private String exchange;
    private String country;
    private MarketBreakdown.MarketCapType marketCapType;
}
