package com.am.common.amcommondata.document.asset.equity;

import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.asset.AssetDocument;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EquityDocument extends AssetDocument {
    private String isin;
    @Field("equity_symbol")
    private String symbol;
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
    private String stockType;
    private String countryOfIncorporation;
}
