package com.am.common.amcommondata.document.portfolio;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSnapshotEntry {

    @Field("portfolioId")
    private String portfolioId;

    @Field("portfolioName")
    private String portfolioName;

    @Field("brokerType")
    private String brokerType;

    @Field("open")
    private Double open;

    @Field("high")
    private Double high;

    @Field("low")
    private Double low;

    @Field("close")
    private Double close;

    @Field("totalInvestment")
    private Double totalInvestment;

    @Field("totalGainLoss")
    private Double totalGainLoss;

    @Field("totalGainLossPercentage")
    private Double totalGainLossPercentage;

    @Field("holdings")
    private List<HoldingSnapshotItem> holdings;
}
