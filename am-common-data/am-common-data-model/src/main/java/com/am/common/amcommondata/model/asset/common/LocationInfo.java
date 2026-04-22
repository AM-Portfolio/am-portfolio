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
public class LocationInfo {
    private String country;
    private String state;
    private String city;
    private String address;
    private String postalCode;
    private String coordinates;
}
