package com.am.common.amcommondata.model.asset.mutualfund;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class MutualFundModel extends AssetModel {
    private String isin;
    private String fundHouse;
    private String category; // Equity, Debt, Hybrid, etc.
    private String subCategory;
    private String schemeType; // Growth, Dividend
    private Double aum; // Assets Under Management
    private Double nav;
    private Double expenseRatio;
    private Double exitLoad;
    private Double minInvestment;
    private String fundManager;
    private LocalDate inceptionDate;
    private Double returnOneYear;
    private Double returnThreeYear;
    private Double returnFiveYear;
    private String riskLevel;
    private String investmentStrategy;
    private String benchmarkIndex;
    private Boolean directPlan;
    private String sipFrequency;
    private Double sipMinimumAmount;
}
