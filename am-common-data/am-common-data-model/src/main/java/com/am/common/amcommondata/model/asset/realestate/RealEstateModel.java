package com.am.common.amcommondata.model.asset.realestate;

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
public class RealEstateModel extends AssetModel {
    private String propertyType; // Residential, Commercial, Industrial
    private Double area;
    private String areaUnit;
    private String location;
    private String address;
    private LocalDate purchaseDate;
    private Double purchasePrice;
    private Double currentValuation;
    private Double rentalIncome;
    private Double maintenanceCost;
    private Double propertyTax;
    private String legalDescription;
    private String zoning;
    private String occupancyStatus; // Rented, Vacant, Owner-occupied
    private LocalDate lastValuationDate;
}
