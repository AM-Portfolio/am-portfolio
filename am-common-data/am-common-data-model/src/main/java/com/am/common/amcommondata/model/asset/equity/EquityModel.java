package com.am.common.amcommondata.model.asset.equity;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class EquityModel extends AssetModel {
    private String isin;
    private String companyName;
    private String sector;
    private String industry;
    private String marketCap;
    private String exchange;
    private Double peRatio;
    private Double pbRatio;
    private Double dividendYield;
    private Double eps;
    private Integer sharesOutstanding;
    private String stockType; // Common, Preferred
    private String countryOfIncorporation;
}
