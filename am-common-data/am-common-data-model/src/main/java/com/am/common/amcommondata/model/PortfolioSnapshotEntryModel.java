package com.am.common.amcommondata.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class PortfolioSnapshotEntryModel {
    private String portfolioId;
    private String portfolioName;
    private String brokerType;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double totalInvestment;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private List<HoldingSnapshotItemModel> holdings;
}

