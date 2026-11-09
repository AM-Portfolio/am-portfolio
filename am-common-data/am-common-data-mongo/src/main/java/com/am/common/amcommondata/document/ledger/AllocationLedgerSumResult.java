package com.am.common.amcommondata.document.ledger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AllocationLedgerSumResult {
    @Id
    private String id; // maps to isin
    private Double totalQuantity;
}
