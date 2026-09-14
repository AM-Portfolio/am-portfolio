package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.mapper.PortfolioMapperv1;
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

    @InjectMocks
    private PortfolioOverviewService portfolioOverviewService;

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
