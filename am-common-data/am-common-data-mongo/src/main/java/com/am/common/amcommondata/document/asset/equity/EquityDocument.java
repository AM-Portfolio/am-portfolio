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

    @Override
    /**
     * Overrides the Lombok-generated getter to provide a schema compatibility fallback.
     * 
     * Rationale:
     * - Groww portfolios store the ticker under 'equity_symbol'.
     * - Upstox portfolios store the ticker under 'symbol'.
     * 
     * By overriding getSymbol(), we first try to return 'equity_symbol'. If it is missing/null,
     * we fall back to the parent class's (AssetDocument) 'symbol' field.
     */
    public String getSymbol() {
        return (this.symbol != null && !this.symbol.isBlank()) ? this.symbol : super.getSymbol();
    }
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
