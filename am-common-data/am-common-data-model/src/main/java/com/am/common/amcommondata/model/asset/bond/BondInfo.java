package com.am.common.amcommondata.model.asset.bond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BondInfo {
    private String issuer;
    private String bondType;
    private Double faceValue;
    private Double couponRate;
    private String couponFrequency;
    private Double yieldToMaturity;
    private String creditRating;
    private Boolean callable;
    private Boolean convertible;
    private String interestPaymentDates;
}
