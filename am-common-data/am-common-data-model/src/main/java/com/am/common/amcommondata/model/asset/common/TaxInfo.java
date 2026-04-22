package com.am.common.amcommondata.model.asset.common;

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
public class TaxInfo {
    private Double taxRate;
    private Double taxAmount;
    private String taxCategory;
    private String taxJurisdiction;
    private Boolean taxExempt;
}
