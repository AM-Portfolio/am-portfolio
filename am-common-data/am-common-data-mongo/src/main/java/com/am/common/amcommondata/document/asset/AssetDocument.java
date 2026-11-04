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
    private Double currentPrice;
    private Double currentValue;
    private Double investmentValue;
    /** Statement/import-day LTP when avg cost is unknown. */
    private Double importMarkPrice;
    private BrokerType brokerType;
    private Double profitLoss;
    private Double profitLossPercentage;
    private Double todayProfitLoss;
    private Double todayProfitLossPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
    private String assetClass;
    private String folioNumber;
    private String schemeCode;
    private String amcName;
    private String rtaName;
    private String advisorName;
    private String ucc;
    private String isin;
    /** NPS-only; stored on portfolio npsModels AssetDocument rows */
    private String tier;
    private String schemeClass;
    private String schemeOption;
    private Double statementNav;
    private Double liveNav;
    private String isinResolveStatus;
    /** NPS-only; stored on portfolio npsModels AssetDocument rows */
    private String pran;
}
