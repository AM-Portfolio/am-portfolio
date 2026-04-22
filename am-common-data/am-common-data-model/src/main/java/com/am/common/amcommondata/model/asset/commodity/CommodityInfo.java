package com.am.common.amcommondata.model.asset.commodity;

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
public class CommodityInfo {
    private String commodityType;
    private String grade;
    private String unit;
    private Double weight;
    private String purity;
    private String storageLocation;
    private String certificateNumber;
    private String custodian;
    private String quality;
    private String origin;
}
