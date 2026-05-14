package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;
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

    @InjectMocks
    private PortfolioOverviewService portfolioOverviewService;

    @Test
    void overviewPortfolio_FromCache() {
        String userId = "user-1";
        PortfolioSummaryV1 cached = PortfolioSummaryV1.builder().build();

        when(portfolioSummaryRedisService.getLatestSummary(userId, TimeInterval.ONE_DAY))
                .thenReturn(Optional.of(cached));

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
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

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNotNull(result);
        verify(portfolioSummaryRedisService).cachePortfolioSummary(any(), eq(userId), any());
    }

    @Test
    void overviewPortfolio_SpecificId_Success() {
        String userId = "user-1";
        UUID portId = UUID.randomUUID();
        PortfolioModelV1 p1 = new PortfolioModelV1();
        p1.setId(portId);
        p1.setTotalValue(500.0);

        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(p1));
        when(portfolioCalculator.calculateSummary(anyList(), anyDouble())).thenReturn(PortfolioSummaryV1.builder().build());

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, portId.toString(), TimeInterval.ONE_DAY);

        assertNotNull(result);
    }

    @Test
    void overviewPortfolio_NoPortfolios_ReturnsNull() {
        String userId = "user-none";
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());

        PortfolioSummaryV1 result = portfolioOverviewService.overviewPortfolio(userId, TimeInterval.ONE_DAY);

        assertNull(result);
    }
}
