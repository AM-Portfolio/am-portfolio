package com.portfolio.model.events.trade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEquityPosition {
    private String symbol;
    private String assetType;

    // --- Entry (BUY) details ---
    private BigDecimal quantity;
    private BigDecimal avgBuyingPrice;
    private BigDecimal investmentValue;

    // --- Exit (SELL) details ---
    private BigDecimal sellQuantity;
    private BigDecimal sellPrice;
    private BigDecimal saleValue;

    // --- P&L ---
    private BigDecimal profitLoss;

    // --- Trade lifecycle state ---
    private String action; // BUY, SELL, UPDATE

    // --- Instrument metadata ---
    private String isin;
    private String sector;
    private String industry;
    private String marketCap;
}
