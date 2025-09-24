package com.portfolio.model.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the OHLC (Open, High, Low, Close) data for a financial instrument.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OhlcData {
    private double open;
    private double high;
    private double low;
    private double close;
}
