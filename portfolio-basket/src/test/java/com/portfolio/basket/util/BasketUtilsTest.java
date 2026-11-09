package com.portfolio.basket.util;

import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasketUtilsTest {

    @Test
    void calculateUserWeights_usesPositiveCostWhenPresent() {
        EquityHoldings a = EquityHoldings.builder().symbol("A").quantity(10.0).investmentCost(1000.0).build();
        EquityHoldings b = EquityHoldings.builder().symbol("B").quantity(10.0).investmentCost(3000.0).build();
        BasketUtils.calculateUserWeights(List.of(a, b));
        assertEquals(25.0, a.getWeightInPortfolio(), 0.01);
        assertEquals(75.0, b.getWeightInPortfolio(), 0.01);
    }

    @Test
    void calculateUserWeights_ignoresZeroInvestmentCost_fallsBackToQuantity() {
        EquityHoldings hdfc = EquityHoldings.builder()
                .symbol("HDFCBANK")
                .isin("INE040A01034")
                .quantity(100.0)
                .investmentCost(0.0)
                .build();
        EquityHoldings tcs = EquityHoldings.builder()
                .symbol("TCS")
                .isin("INE467B01029")
                .quantity(300.0)
                .investmentCost(0.0)
                .build();
        BasketUtils.calculateUserWeights(List.of(hdfc, tcs));
        assertEquals(25.0, hdfc.getWeightInPortfolio(), 0.01);
        assertEquals(75.0, tcs.getWeightInPortfolio(), 0.01);
    }

    @Test
    void calculateUserWeights_nullCostUsesQuantityShare() {
        EquityHoldings a = EquityHoldings.builder().symbol("A").quantity(1.0).build();
        EquityHoldings b = EquityHoldings.builder().symbol("B").quantity(1.0).build();
        BasketUtils.calculateUserWeights(List.of(a, b));
        assertEquals(50.0, a.getWeightInPortfolio(), 0.01);
        assertEquals(50.0, b.getWeightInPortfolio(), 0.01);
        assertTrue(BasketUtils.positiveMoneyValue(a) == 0.0);
    }
}
