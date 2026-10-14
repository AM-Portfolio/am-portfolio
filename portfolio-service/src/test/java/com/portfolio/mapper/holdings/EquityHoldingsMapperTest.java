package com.portfolio.mapper.holdings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

class EquityHoldingsMapperTest {

    private EquityHoldingsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EquityHoldingsMapper();
    }

    @Test
    void usesAvgTimesQty_whenAvgPositive() {
        EquityModel eq = EquityModel.builder()
                .symbol("TCS")
                .quantity(10.0)
                .avgBuyingPrice(100.0)
                .investmentValue(999.0)
                .build();

        EquityHoldings h = mapper.toEquityHoldings(eq);

        assertEquals(1000.0, h.getInvestmentCost(), 0.001);
        assertEquals(100.0, h.getAverageBuyingPrice(), 0.001);
    }

    @Test
    void derivesAvgAndCost_fromInvestmentValue_whenAvgZero() {
        EquityModel eq = EquityModel.builder()
                .symbol("RELIANCE")
                .quantity(5.0)
                .avgBuyingPrice(0.0)
                .investmentValue(2500.0)
                .build();

        EquityHoldings h = mapper.toEquityHoldings(eq);

        assertEquals(2500.0, h.getInvestmentCost(), 0.001);
        assertEquals(500.0, h.getAverageBuyingPrice(), 0.001);
    }

    @Test
    void keepsZeroCost_whenAvgAndInvestmentMissing() {
        EquityModel eq = EquityModel.builder()
                .symbol("INFY")
                .quantity(3.0)
                .avgBuyingPrice(0.0)
                .investmentValue(0.0)
                .build();

        EquityHoldings h = mapper.toEquityHoldings(eq);

        assertEquals(0.0, h.getInvestmentCost(), 0.001);
        assertEquals(0.0, h.getAverageBuyingPrice(), 0.001);
    }

    @Test
    void doesNotInventAvg_whenQuantityMissing() {
        EquityModel eq = EquityModel.builder()
                .symbol("WIPRO")
                .quantity(null)
                .avgBuyingPrice(null)
                .investmentValue(1000.0)
                .build();

        EquityHoldings h = mapper.toEquityHoldings(eq);

        assertEquals(0.0, h.getInvestmentCost(), 0.001);
        assertNull(h.getAverageBuyingPrice());
    }
}
