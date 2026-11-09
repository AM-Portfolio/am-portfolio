package com.am.common.amcommondata.document.ledger;

import com.am.common.amcommondata.model.ledger.AllocationLedgerStatus;
import com.am.common.amcommondata.model.ledger.AllocationLedgerEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "allocation_ledger")
@CompoundIndexes({
        @CompoundIndex(name = "basket_status_idx", def = "{'basketId': 1, 'status': 1}"),
        @CompoundIndex(name = "portfolio_isin_status_idx", def = "{'brokerPortfolioId': 1, 'isin': 1, 'status': 1}")
})
public class AllocationLedgerEntry {
    @Id
    private String id;
    
    private String basketId;
    private String brokerPortfolioId;
    private String isin;
    private String symbol;
    private Double quantity;
    
    private AllocationLedgerStatus status;
    private AllocationLedgerEventType eventType;
    
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
    private String releasedBy;
    private String reason;
}
