package com.am.common.amcommondata.model.asset.realestate;

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
public class PropertyInfo {
    private String propertyType;
    private Double area;
    private String areaUnit;
    private Integer yearBuilt;
    private String condition;
    private String zoning;
    private String landTitle;
    private String propertyId;
    private String legalDescription;
    private String amenities;
    private String utilities;
    private String parking;
    private String propertyTax;
    private String insurance;
}
