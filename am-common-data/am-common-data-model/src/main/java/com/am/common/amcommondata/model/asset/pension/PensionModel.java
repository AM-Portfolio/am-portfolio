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
public class PensionModel extends AssetModel {
    private String schemeType; // NPS, EPF, PPF, etc.
    private String accountNumber;
    private String pran; // Permanent Retirement Account Number
    private LocalDate enrollmentDate;
    private Double employeeContribution;
    private Double employerContribution;
    private Double governmentContribution;
    private String fundManager;
    private String assetAllocation;
    private LocalDate maturityDate;
    private Double expectedCorpus;
    private Double currentReturn;
    private String taxBenefits;
    private String withdrawalRules;
    private String nomineeDetails;
}
