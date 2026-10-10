package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.am.observability.flow.FlowLogger;
import com.am.observability.flow.FlowSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioOverviewServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PortfolioHoldingsService portfolioHoldingsService;

    @Mock
    private PortfolioMapperv1 portfolioMapper;

    @Mock
    private PortfolioSummaryRedisService portfolioSummaryRedisService;

    @Mock
    private PortfolioCalculator portfolioCalculator;

    @Mock
    private PortfolioSummaryMongoService portfolioSummaryMongoService;

    @Mock
    private com.am.common.amcommondata.service.PortfolioSnapshotService portfolioSnapshotService;

    @Mock
    private FlowLogger flowLogger;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private PortfolioOverviewService portfolioOverviewService;

    @Test
    void overviewPortfolio_fillsTodayGainLossFromPriorSnapshot_whenLiveNull() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .build();
        com.portfolio.model.portfolio.PortfolioHoldings holdings =
                com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of(
                                com.portfolio.model.portfolio.EquityHoldings.builder()
                                        .symbol("PWL")
                                        .investmentCost(1443.79)
                                        .currentValue(1393.51)
                                        .todayGainLoss(null)
                                        .build()))
                        .priceFreshness("AS_OF")
                        .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel snap =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(snap.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(snap.getTotalUserWealth()).thenReturn(1443.79);
        lenient().when(snap.getPortfolios()).thenReturn(List.of());

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        when(portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true))
                .thenReturn(holdings);
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble()))
                .thenReturn(PortfolioSummaryV1.builder()
                        .currentValue(1393.51)
                        .todayGainLoss(null)
                        .build());
        lenient().when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(snap));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result.getTodayGainLoss());
        assertEquals(-50.28, result.getTodayGainLoss(), 0.01);
    }

    @Test
    void overviewPortfolio_overwritesAsOfZeroTodayGain_withLtpVsPriorSession() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(634072.0)
                .currentValue(630180.26)
                .todayGainLoss(0.0)
                .todayGainLossPercentage(0.0)
                .build();
        com.portfolio.model.portfolio.PortfolioHoldings holdings =
                com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of(
                                com.portfolio.model.portfolio.EquityHoldings.builder()
                                        .symbol("RELIANCE")
                                        .investmentCost(634072.0)
                                        .currentValue(630255.63)
                                        .todayGainLoss(0.0)
                                        .build()))
                        .priceFreshness("AS_OF")
                        .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(630180.26);
        lenient().when(friday.getPortfolios()).thenReturn(List.of());

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_WEEK))
                .thenReturn(Optional.of(cached));
        when(portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_WEEK, true))
                .thenReturn(holdings);
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble()))
                .thenReturn(PortfolioSummaryV1.builder()
                        .currentValue(630255.63)
                        .todayGainLoss(0.0)
                        .todayGainLossPercentage(0.0)
                        .build());
        lenient().when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_WEEK);

        assertNotNull(result.getTodayGainLoss());
        // LTP mark − last session close (not stuck at 0)
        assertEquals(75.37, result.getTodayGainLoss(), 0.01);
    }

    @Test
    void overviewPortfolio_fillsTodayGainFromHoldingsHist_whenMarkEqualsFridaySnapAndOpenEqualsClose() {
        String userId = "user-upstox";
        String portfolioId = "626cccfd-7bf2-4dbd-a3ee-797e53e8922a";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .totalGainLoss(-50.28)
                .build();
        com.portfolio.model.portfolio.PortfolioHoldings holdings =
                com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of(
                                com.portfolio.model.portfolio.EquityHoldings.builder()
                                        .symbol("PWL")
                                        .quantity(10.0)
                                        .currentPrice(139.351)
                                        .investmentCost(1443.79)
                                        .currentValue(1393.51)
                                        .todayGainLoss(null)
                                        .build()))
                        .priceFreshness("AS_OF")
                        .build();
        // Single prior session: mark == Friday close, open == close (legacy EOD) → snapshot path fails
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(1393.51);
        when(friday.getPortfolios()).thenReturn(List.of());

        MarketData quote = MarketData.builder()
                .symbol("PWL")
                .lastPrice(139.351)
                .previousClose(139.351) // collapsed after hours
                .build();
        MarketData.MarketDataPoint thuBar = MarketData.MarketDataPoint.builder()
                .ohlcData(OhlcData.builder().close(144.379).open(144.379).high(144.379).low(144.379).build())
                .build();
        MarketData.MarketDataPoint friBar = MarketData.MarketDataPoint.builder()
                .ohlcData(OhlcData.builder().close(139.351).open(139.351).high(139.351).low(139.351).build())
                .build();
        MarketData hist = MarketData.builder()
                .symbol("PWL")
                .dataPoints(List.of(thuBar, friBar))
                .build();

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY, portfolioId))
                .thenReturn(Optional.of(cached));
        when(portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true))
                .thenReturn(holdings);
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble()))
                .thenReturn(PortfolioSummaryV1.builder()
                        .currentValue(1393.51)
                        .investmentValue(1443.79)
                        .totalGainLoss(-50.28)
                        .todayGainLoss(null)
                        .build());
        lenient().when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(friday));
        when(marketDataService.getMarketData(anyList())).thenReturn(Map.of("PWL", quote));
        when(marketDataService.getHistoricalData(
                anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("PWL", hist));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result =
                portfolioOverviewService.overviewPortfolio(userId, portfolioId, TimeInterval.ONE_DAY);

        assertNotNull(result.getTodayGainLoss());
        // 10 × (139.351 − 144.379) = −50.28; 1D total mirrors session day (not cost-basis masquerade)
        assertEquals(-50.28, result.getTodayGainLoss(), 0.02);
        assertEquals(-50.28, result.getTotalGainLoss(), 0.02);
    }

    @Test
    void overviewPortfolio_freezesDayPnLFromTwoPriorSessions_whenMarkUnchanged() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .build();
        com.portfolio.model.portfolio.PortfolioHoldings holdings =
                com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of(
                                com.portfolio.model.portfolio.EquityHoldings.builder()
                                        .symbol("PWL")
                                        .investmentCost(1443.79)
                                        .currentValue(1393.51)
                                        .todayGainLoss(null)
                                        .build()))
                        .priceFreshness("AS_OF")
                        .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel thursday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(thursday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 8));
        when(thursday.getTotalUserWealth()).thenReturn(1443.79);
        lenient().when(thursday.getPortfolios()).thenReturn(List.of());
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(1393.51);
        lenient().when(friday.getPortfolios()).thenReturn(List.of());

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        when(portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true))
                .thenReturn(holdings);
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble()))
                .thenReturn(PortfolioSummaryV1.builder()
                        .currentValue(1393.51)
                        .todayGainLoss(null)
                        .build());
        lenient().when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(thursday, friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result.getTodayGainLoss());
        // Frozen Friday session: 1393.51 − 1443.79
        assertEquals(-50.28, result.getTodayGainLoss(), 0.01);
    }

    @Test
    void overviewPortfolio_FromCache() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1000.0)
                .currentValue(1100.0)
                .todayGainLoss(0.0)
                .build();
        com.portfolio.model.portfolio.PortfolioHoldings holdings =
                com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of(
                                com.portfolio.model.portfolio.EquityHoldings.builder()
                                        .symbol("TCS")
                                        .investmentCost(1000.0)
                                        .currentValue(1110.0)
                                        .todayGainLoss(10.0)
                                        .todayGainLossPercentage(0.9)
                                        .build()))
                        .asOf(java.time.LocalDateTime.now())
                        .priceFreshness("AS_OF")
                        .sessionDate(java.time.LocalDate.of(2026, 9, 12))
                        .build();

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        when(portfolioHoldingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true))
                .thenReturn(holdings);
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble()))
                .thenReturn(PortfolioSummaryV1.builder()
                        .currentValue(1110.0)
                        .todayGainLoss(10.0)
                        .todayGainLossPercentage(0.9)
                        .build());
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
        assertEquals(10.0, result.getTodayGainLoss());
        assertEquals("AS_OF", result.getPriceFreshness());
        verify(portfolioHoldingsService).getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
        verifyNoInteractions(portfolioService);
    }

    @Test
    void overviewPortfolio_CacheMiss_Success() {
        String userId = "user-1";
        PortfolioModelV1 p1 = new PortfolioModelV1();
        p1.setId(UUID.randomUUID());
        p1.setBrokerType(BrokerType.ZERODHA);
        p1.setTotalValue(1000.0);

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.empty());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(p1));
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble())).thenReturn(PortfolioSummaryV1.builder().build());
        when(portfolioMapper.toPortfolioModelV1(any(PortfolioModelV1.class))).thenReturn(com.portfolio.model.portfolio.v1.BrokerPortfolioSummary.builder().build());
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
        verify(portfolioSummaryRedisService).cachePortfolioSummary(any(), eq(userId), any(), isNull());
    }

    @Test
    void overviewPortfolio_SpecificId_Success() {
        String userId = "user-1";
        UUID portId = UUID.randomUUID();
        PortfolioModelV1 p1 = new PortfolioModelV1();
        p1.setId(portId);
        p1.setOwner(userId);
        p1.setBrokerType(BrokerType.ZERODHA);
        p1.setTotalValue(500.0);

        when(portfolioService.getPortfolioById(portId)).thenReturn(p1);
        when(portfolioHoldingsService.getPortfolioHoldings(eq(userId), eq(portId.toString()), any(), eq(true)))
                .thenReturn(com.portfolio.model.portfolio.PortfolioHoldings.builder()
                        .equityHoldings(List.of())
                        .build());
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble())).thenReturn(PortfolioSummaryV1.builder().build());
        when(portfolioMapper.toPortfolioModelV1(any(PortfolioModelV1.class))).thenReturn(com.portfolio.model.portfolio.v1.BrokerPortfolioSummary.builder().build());
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, portId.toString(), TimeInterval.ONE_DAY);

        assertNotNull(result);
    }

    @Test
    void overviewPortfolio_NoPortfolios_ReturnsEmptySummary() {
        String userId = "user-none";
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
        assertEquals(0.0, result.getInvestmentValue());
    }
}
