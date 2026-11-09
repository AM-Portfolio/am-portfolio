package com.portfolio.model.analytics.intelligence;

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
public class WhatIfRequest {
    /** ADD_INVESTMENT | MODIFY_HOLDING | SWITCH_ALLOCATION */
    private String mode;
    private String symbol;
    private Double amountInr;
    private Double targetWeightPct;
    private String fromSector;
    private String toSector;
    private Double moveWeightPct;
}
