package com.am.common.amcommondata.model.asset.commodity;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class CommodityModel extends AssetModel {
    private String commodityType; // Gold, Silver, Oil, etc.
    private String grade;
    private String purity;
    private Double weight;
    private String weightUnit;
    private String storageLocation;
    private String custodian;
    private String certificateNumber;
    private String form; // Physical, ETF, Futures
    private Double storageCharges;
    private String contractDetails;
    private String deliveryOption;
    private String exchangeCode;
}
