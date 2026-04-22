package com.am.common.amcommondata.model.asset.bond;

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
public class BondModel extends AssetModel {
    private String bondType; // Government, Corporate, Municipal
    private String issuer;
    private Double faceValue;
    private Double couponRate;
    private String couponFrequency;
    private LocalDate issueDate;
    private LocalDate maturityDate;
    private String creditRating;
    private Double yieldToMaturity;
    private Double currentYield;
    private Boolean isCallable;
    private Double callPrice;
    private String interestPaymentType; // Fixed, Floating
    private String isin;
    private String currency;
    private Double minimumInvestment;
    private String taxStatus;
}
