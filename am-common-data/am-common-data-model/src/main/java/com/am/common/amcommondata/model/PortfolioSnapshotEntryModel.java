package com.am.common.amcommondata.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double open;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double high;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double low;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double close;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalInvestment;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalGainLoss;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalGainLossPercentage;
    /** Kept for in-process use; history JSON must not emit this. Catch-up reads Mongo documents. */
    @JsonIgnore
    private List<HoldingSnapshotItemModel> holdings;
}

