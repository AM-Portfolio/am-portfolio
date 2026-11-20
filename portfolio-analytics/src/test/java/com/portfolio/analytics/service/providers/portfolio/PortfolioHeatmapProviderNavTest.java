package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MutualFundNavMark;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.MutualFundNavService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.portfolio.HoldingInstrumentClassifier;
import com.portfolio.redis.service.PortfolioHeatmapRedisService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.ReflectionTestUtils.invokeMethod;
import static org.mockito.Mockito.mock;

class PortfolioHeatmapProviderNavTest {
    @Test
    void schemeHeatmapUsesNavAndPriorNavInsteadOfPoisonedEquityQuote() {
        String isin = "INF000000001";
        PortfolioHeatmapProvider provider = new PortfolioHeatmapProvider(
                mock(PortfolioService.class),
                mock(MarketDataService.class),
                mock(SecurityDetailsService.class),
                mock(PortfolioHeatmapRedisService.class),
                mock(MutualFundNavService.class));
        EquityModel holding = EquityModel.builder()
                .symbol(isin)
                .isin(isin)
                .assetClass("mutual_fund_demat")
                .name("Example Scheme")
                .quantity(4.0)
                .currentValue(40.0)
                .build();
        Map<String, List<MarketData>> bySector = new HashMap<>();
        Map<String, List<Double>> quantitiesBySector = new HashMap<>();
        Map<String, String> priceSources = new HashMap<>();
        double[] totalValue = {0.0};

        invokeMethod(provider, "groupMarketDataBySector",
                Map.of(isin, MarketData.builder().symbol(isin).lastPrice(999.0).previousClose(500.0).build()),
                Map.of("Technology", List.of(isin)),
                Map.of(isin, 4.0),
                Map.of(isin, holding),
                Map.of(isin, new MutualFundNavMark(isin, "Example Scheme", 12.5, 11.0, LocalDate.now())),
                bySector,
                quantitiesBySector,
                priceSources,
                totalValue);

        MarketData priced = bySector.get(HoldingInstrumentClassifier.SECTOR_MUTUAL_FUND).get(0);
        assertEquals(12.5, priced.getLastPrice());
        assertEquals(11.0, priced.getPreviousClose());
        assertEquals(HoldingInstrumentClassifier.PRICE_NAV, priceSources.get(isin));
        assertEquals(50.0, totalValue[0]);
    }
}
