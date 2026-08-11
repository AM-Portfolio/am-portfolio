package com.am.common.amcommondata.document.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingAllocationDocument {
    @Field("basketPortfolioId")
    private String basketPortfolioId;

    @Field("isin")
    private String isin;

    @Field("symbol")
    private String symbol;

    @Field("quantity")
    private Double quantity;
}
