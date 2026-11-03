package com.portfolio.mapper.holdings;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquityHoldingsMapperTest {

    private final EquityHoldingsMapper mapper = new EquityHoldingsMapper();

    @Test
    void unknownCost_emitsNullInvestmentCost_andCapturesImportMark() {
        EquityModel equity = EquityModel.builder()
                .symbol("RELIANCE")
                .quantity(10.0)
                .currentPrice(1400.0)
                .build();

        EquityHoldings holding = mapper.toEquityHoldings(equity);

        assertNull(holding.getInvestmentCost());
        assertNull(holding.getAverageBuyingPrice());
        assertEquals(1400.0, holding.getImportMarkPrice());
    }

    @Test
    void knownCost_keepsInvestment_andIgnoresImportMarkCapture() {
        EquityModel equity = EquityModel.builder()
                .symbol("TCS")
                .quantity(2.0)
                .avgBuyingPrice(3000.0)
                .currentPrice(3200.0)
                .build();

        EquityHoldings holding = mapper.toEquityHoldings(equity);

        assertEquals(6000.0, holding.getInvestmentCost());
        assertEquals(3000.0, holding.getAverageBuyingPrice());
        assertNull(holding.getImportMarkPrice());
    }

    @Test
    void durableImportMark_isPreferredOverCurrentPrice() {
        EquityModel equity = EquityModel.builder()
                .symbol("INFY")
                .quantity(5.0)
                .importMarkPrice(1500.0)
                .currentPrice(1600.0)
                .build();

        EquityHoldings holding = mapper.toEquityHoldings(equity);

        assertEquals(1500.0, holding.getImportMarkPrice());
        assertNull(holding.getInvestmentCost());
    }
}
