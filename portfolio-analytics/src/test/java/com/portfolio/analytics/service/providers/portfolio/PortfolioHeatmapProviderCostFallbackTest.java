package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioHeatmapProviderCostFallbackTest {

    @Test
    void costBasisPrice_prefersAvgThenInvestmentThenImportMarkThenCurrent() {
        EquityModel withAvg = EquityModel.builder().avgBuyingPrice(100.0).importMarkPrice(90.0).build();
        assertEquals(100.0, PortfolioHeatmapProvider.costBasisPrice(withAvg));

        EquityModel withInv = EquityModel.builder()
                .investmentValue(200.0)
                .quantity(2.0)
                .importMarkPrice(90.0)
                .build();
        assertEquals(100.0, PortfolioHeatmapProvider.costBasisPrice(withInv));

        EquityModel markOnly = EquityModel.builder()
                .importMarkPrice(250.0)
                .currentPrice(240.0)
                .build();
        assertEquals(250.0, PortfolioHeatmapProvider.costBasisPrice(markOnly));

        EquityModel statementOnly = EquityModel.builder().currentPrice(240.0).build();
        assertEquals(240.0, PortfolioHeatmapProvider.costBasisPrice(statementOnly));

        assertNull(PortfolioHeatmapProvider.costBasisPrice(EquityModel.builder().build()));
    }
}
