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
        com.am.common.amcommondata.model.PortfolioSnapshotModel snap =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(snap.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(snap.getTotalUserWealth()).thenReturn(1443.79);
        lenient().when(snap.getPortfolios()).thenReturn(List.of());
        lenient().when(snap.getSessionDayGainLoss()).thenReturn(null);

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(snap));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result.getTodayGainLoss());
        assertEquals(-50.28, result.getTodayGainLoss(), 0.01);
        // Total Return stays overall (mark − invested), independent of session day fill
        assertEquals(-50.28, result.getTotalGainLoss(), 0.01);
        verify(portfolioHoldingsService, never()).getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
    }

    @Test
    void overviewPortfolio_overwritesAsOfZeroTodayGain_withLtpVsPriorSession() {
        // Warm path keeps cached mark (no sync reprice). Collapsed AS_OF zero is overwritten
        // via LTP mark − prior session close when mark != last session wealth.
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(634072.0)
                .currentValue(630255.63)
                .todayGainLoss(0.0)
                .todayGainLossPercentage(0.0)
                .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(630180.26);
        lenient().when(friday.getPortfolios()).thenReturn(List.of());
        lenient().when(friday.getSessionDayGainLoss()).thenReturn(null);

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_WEEK))
                .thenReturn(Optional.of(cached));
        when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_WEEK);

        assertNotNull(result.getTodayGainLoss());
        // LTP mark − last session close (not stuck at 0)
        assertEquals(75.37, result.getTodayGainLoss(), 0.01);
        verify(portfolioHoldingsService, never()).getPortfolioHoldings(userId, TimeInterval.ONE_WEEK, true);
        verify(marketDataService, never()).getMarketData(anyList());
    }

    @Test
    void overviewPortfolio_prefersSnapshotSessionDayFreeze_whenOpenEqualsCloseAndHistEmpty() {
        String userId = "user-freeze";
        String portfolioId = "a63054e7-582c-4f45-aa0b-e5d7adceedc2";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .totalGainLoss(-50.28)
                .build();
        com.am.common.amcommondata.model.PortfolioSnapshotEntryModel entry =
                com.am.common.amcommondata.model.PortfolioSnapshotEntryModel.builder()
                        .portfolioId(portfolioId)
                        .open(1393.51)
                        .close(1393.51)
                        .sessionDayGainLoss(-12.34)
                        .sessionDayGainLossPercentage(-0.88)
                        .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                com.am.common.amcommondata.model.PortfolioSnapshotModel.builder()
                        .snapshotDate(java.time.LocalDate.of(2026, 10, 9))
                        .totalUserWealth(1393.51)
                        .totalUserWealthOpen(1393.51)
                        .portfolios(List.of(entry))
                        .sessionDayGainLoss(-12.34)
                        .sessionDayGainLossPercentage(-0.88)
                        .build();

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY, portfolioId))
                .thenReturn(Optional.of(cached));
        when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result =
                portfolioOverviewService.overviewPortfolio(userId, portfolioId, TimeInterval.ONE_DAY);

        assertEquals(-12.34, result.getTodayGainLoss(), 0.01);
        assertEquals(-50.28, result.getTotalGainLoss(), 0.01);
        assertEquals("AS_OF", result.getPriceFreshness());
        verify(portfolioHoldingsService, never())
                .getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);
        verify(marketDataService, never()).getMarketData(anyList());
    }

    @Test
    void overviewPortfolio_warmCache_skipsHoldingsMarketHist_whenSnapshotSessionDayMissing() {
        // Warm path must not call market OHLC/hist (hang risk). Without usable snapshot session
        // day fields, todayGainLoss stays unset; Total Return is preserved from cache.
        String userId = "user-upstox";
        String portfolioId = "626cccfd-7bf2-4dbd-a3ee-797e53e8922a";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .totalGainLoss(-50.28)
                .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(1393.51);
        when(friday.getPortfolios()).thenReturn(List.of());
        lenient().when(friday.getSessionDayGainLoss()).thenReturn(null);

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY, portfolioId))
                .thenReturn(Optional.of(cached));
        lenient().when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result =
                portfolioOverviewService.overviewPortfolio(userId, portfolioId, TimeInterval.ONE_DAY);

        assertEquals(-50.28, result.getTotalGainLoss(), 0.02);
        assertEquals("AS_OF", result.getPriceFreshness());
        verify(portfolioHoldingsService, never())
                .getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);
        verify(marketDataService, never()).getMarketData(anyList());
        verify(marketDataService, never()).getHistoricalData(
                anyList(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void overviewPortfolio_freezesDayPnLFromTwoPriorSessions_whenMarkUnchanged() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1443.79)
                .currentValue(1393.51)
                .build();
        com.am.common.amcommondata.model.PortfolioSnapshotModel thursday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(thursday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 8));
        when(thursday.getTotalUserWealth()).thenReturn(1443.79);
        lenient().when(thursday.getPortfolios()).thenReturn(List.of());
        lenient().when(thursday.getSessionDayGainLoss()).thenReturn(null);
        com.am.common.amcommondata.model.PortfolioSnapshotModel friday =
                mock(com.am.common.amcommondata.model.PortfolioSnapshotModel.class);
        when(friday.getSnapshotDate()).thenReturn(java.time.LocalDate.of(2026, 10, 9));
        when(friday.getTotalUserWealth()).thenReturn(1393.51);
        lenient().when(friday.getPortfolios()).thenReturn(List.of());
        lenient().when(friday.getSessionDayGainLoss()).thenReturn(null);

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        when(portfolioSnapshotService.getHistory(eq(userId), any(), anyString()))
                .thenReturn(List.of(thursday, friday));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result.getTodayGainLoss());
        // Frozen Friday session: 1393.51 − 1443.79
        assertEquals(-50.28, result.getTodayGainLoss(), 0.01);
        assertEquals(-50.28, result.getTotalGainLoss(), 0.01);
        verify(portfolioHoldingsService, never()).getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
    }

    @Test
    void overviewPortfolio_FromCache() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder()
                .investmentValue(1000.0)
                .currentValue(1100.0)
                .totalGainLoss(100.0)
                .todayGainLoss(10.0)
                .priceFreshness("LIVE")
                .build();

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));
        lenient().when(flowLogger.start(anyString(), any())).thenReturn(mock(FlowSpan.class));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
        assertEquals(1000.0, result.getInvestmentValue());
        assertEquals(1100.0, result.getCurrentValue());
        assertEquals(10.0, result.getTodayGainLoss());
        assertEquals("AS_OF", result.getPriceFreshness());
        assertEquals("CACHE", result.getPriceSource());
        verify(portfolioHoldingsService, never()).getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true);
        verify(portfolioCalculator, never()).calculateSummary(anyList(), anyDouble());
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
