package com.am.common.amcommondata.model.asset;

import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.asset.common.MarketDataModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class AssetModel {
    private UUID id;
    private String symbol;
    private String name;
    private String description;
    private AssetType assetType;
    private MarketDataModel marketData;
    private Double quantity;
    private Double avgBuyingPrice;
    private Double currentValue;
    private Double investmentValue;
    private BrokerType brokerType;
    private Double profitLoss;
    private Double profitLossPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
}
