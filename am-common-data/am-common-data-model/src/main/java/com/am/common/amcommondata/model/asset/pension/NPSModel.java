package com.am.common.amcommondata.model.asset.pension;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class NPSModel extends AssetModel {
    private String pran; // Permanent Retirement Account Number
    private String tier; // Tier 1, Tier 2
    private String schemeType; // Auto Choice, Active Choice
    private Double employeeContribution;
    private Double employerContribution;
    private Double governmentContribution;
    private String assetAllocation;
    private String fundManager;
    private Double equityPercentage;
    private Double corporateBondPercentage;
    private Double governmentBondPercentage;
    private Double alternativeInvestmentPercentage;
    private LocalDate enrollmentDate;
    private LocalDate retirementDate;
    private Double currentReturn;
    private Double totalAccumulatedValue;
    private String nomineeDetails;
    private String withdrawalRules;
}
