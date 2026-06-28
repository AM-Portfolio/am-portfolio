package com.am.common.amcommondata.document.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingSnapshotItem {
    private String symbol;
    private String isin;
    private Double quantity;
    private Double avgBuyPrice;
}
