package com.am.common.amcommondata.model.asset.fixeddeposit;

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
public class FixedDepositModel extends AssetModel {
    private String bankName;
    private String accountNumber;
    private Double principalAmount;
    private Double interestRate;
    private String interestPayoutFrequency;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private Integer tenureMonths;
    private String depositType; // Cumulative, Non-Cumulative
    private Double maturityAmount;
    private String nomineeDetails;
    private Boolean autoRenewal;
    private String taxSavingScheme;
    private Double tdsRate;
    private String prematureWithdrawalTerms;
    private String loanFacility;
    private String branchDetails;
    private Boolean seniorCitizen;
}
