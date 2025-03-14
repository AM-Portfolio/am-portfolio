package com.portfolio.model;

import com.portfolio.model.enums.BrokerType;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class Transaction {
    private String transactionId;
    private TransactionType type;
    private String assetId;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private LocalDateTime transactionDate;
    private BrokerType brokerType;
    private String notes;
    
    public enum TransactionType {
        BUY,
        SELL,
        DIVIDEND,
        STOCK_SPLIT,
        MERGER,
        SPINOFF,
        RIGHTS_OFFERING,
        INTEREST_PAYMENT
    }
}
