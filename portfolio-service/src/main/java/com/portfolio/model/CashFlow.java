package com.portfolio.model;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashFlow {
    private String cashFlowId;
    private CashFlowType type;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private String portfolioId;
    private String description;

    public enum CashFlowType {
        DEPOSIT,
        WITHDRAWAL,
        DIVIDEND,
        INTEREST,
        FEE,
        TAX
    }
}
