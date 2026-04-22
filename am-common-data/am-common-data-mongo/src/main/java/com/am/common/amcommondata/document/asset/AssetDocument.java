package com.am.common.amcommondata.document.asset;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.model.asset.common.MarketDataModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssetDocument extends BaseDocument {
    @Field("symbol")
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
