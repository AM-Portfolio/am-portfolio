package com.portfolio.app.scheduler;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioHistorySchedulerTest {

    @InjectMocks
    private PortfolioHistoryScheduler scheduler;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PortfolioHoldingsService portfolioHoldingsService;

    @Mock
    private PortfolioSnapshotService portfolioSnapshotService;

    @Test
    void whenJobRuns_thenSnapshotsAreSavedForEachUser() {
        UUID portfolioId = UUID.randomUUID();
        when(portfolioService.getAllUserIds()).thenReturn(List.of("user1", "user2"));
        when(portfolioService.getPortfoliosByUserId("user1"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(portfolioId).name("P1").build()));
        when(portfolioService.getPortfoliosByUserId("user2")).thenReturn(List.of());

        EquityHoldings equity = EquityHoldings.builder()
                .symbol("TCS")
                .quantity(10.0)
                .averageBuyingPrice(100.0)
                .currentPrice(110.0)
                .build();
        PortfolioHoldings holdings = PortfolioHoldings.builder()
                .equityHoldings(List.of(equity))
                .build();

        when(portfolioHoldingsService.getPortfolioHoldings(
                eq("user1"), eq(portfolioId.toString()), eq(TimeInterval.ONE_DAY), eq(true)))
                .thenReturn(holdings);

        scheduler.runEndOfDayJob();

        verify(portfolioSnapshotService).saveUserSnapshot(
                eq("user1"), anyDouble(), anyDouble(), anyDouble(), anyDouble(), argThat(entries -> entries.size() == 1));
        verify(portfolioHoldingsService, never()).getPortfolioHoldings(
                eq("user2"), anyString(), any(), anyBoolean());
    }

    @Test
    void whenUserProcessingFails_thenContinueWithNextUser() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(portfolioService.getAllUserIds()).thenReturn(List.of("fail-user", "success-user"));
        when(portfolioService.getPortfoliosByUserId("fail-user"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(p1).build()));
        when(portfolioService.getPortfoliosByUserId("success-user"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(p2).build()));

        doThrow(new RuntimeException("Simulated Failure"))
                .when(portfolioHoldingsService)
                .getPortfolioHoldings(eq("fail-user"), eq(p1.toString()), any(), eq(true));

        PortfolioHoldings holdings = PortfolioHoldings.builder()
                .equityHoldings(List.of(EquityHoldings.builder()
                        .symbol("INFY")
                        .quantity(5.0)
                        .averageBuyingPrice(200.0)
                        .currentPrice(210.0)
                        .build()))
                .build();
        when(portfolioHoldingsService.getPortfolioHoldings(
                eq("success-user"), eq(p2.toString()), eq(TimeInterval.ONE_DAY), eq(true)))
                .thenReturn(holdings);

        scheduler.runEndOfDayJob();

        verify(portfolioSnapshotService).saveUserSnapshot(
                eq("success-user"), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
    }

    @Test
    void whenGetAllUsersFails_thenHandleGracefully() {
        when(portfolioService.getAllUserIds()).thenThrow(new RuntimeException("DB Failure"));

        scheduler.runEndOfDayJob();

        verifyNoInteractions(portfolioHoldingsService, portfolioSnapshotService);
    }
}
