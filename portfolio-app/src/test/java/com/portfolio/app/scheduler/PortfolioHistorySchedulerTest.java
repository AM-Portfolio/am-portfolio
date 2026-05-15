package com.portfolio.app.scheduler;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for PortfolioHistoryScheduler.
 * Moved to com.portfolio.app.scheduler for consistent scanning.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioHistorySchedulerTest {

    @InjectMocks
    private PortfolioHistoryScheduler scheduler;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PortfolioHoldingsService portfolioHoldingsService;

    @Test
    void whenJobRuns_thenAllUserHistoriesAreProcessed() {
        // Prepare test data
        when(portfolioService.getAllUserIds()).thenReturn(Arrays.asList("user1", "user2", "user3"));

        // Execute job manually
        scheduler.runEndOfDayJob();

        // Verify that holdings service was called for each user with forceEnrich=true
        verify(portfolioHoldingsService, times(3)).getPortfolioHoldings(anyString(), any(TimeInterval.class), eq(true));
        verify(portfolioHoldingsService).getPortfolioHoldings(eq("user1"), any(), eq(true));
        verify(portfolioHoldingsService).getPortfolioHoldings(eq("user2"), any(), eq(true));
        verify(portfolioHoldingsService).getPortfolioHoldings(eq("user3"), any(), eq(true));
    }

    @Test
    void whenUserProcessingFails_thenContinueWithNextUser() {
        // Prepare test data where one user fails
        when(portfolioService.getAllUserIds()).thenReturn(Arrays.asList("fail-user", "success-user"));
        
        doThrow(new RuntimeException("Simulated Failure"))
            .when(portfolioHoldingsService).getPortfolioHoldings(eq("fail-user"), any(), anyBoolean());

        // Execute job
        scheduler.runEndOfDayJob();

        // Verify that even if one fails, the other is still processed
        verify(portfolioHoldingsService).getPortfolioHoldings(eq("fail-user"), any(), eq(true));
        verify(portfolioHoldingsService).getPortfolioHoldings(eq("success-user"), any(), eq(true));
    }

    @Test
    void whenGetAllUsersFails_thenHandleGracefully() {
        // Prepare test data where fetching users fails
        when(portfolioService.getAllUserIds()).thenThrow(new RuntimeException("DB Failure"));

        // Execute job
        scheduler.runEndOfDayJob();

        // Verify no holdings processing was attempted
        verifyNoInteractions(portfolioHoldingsService);
    }
}
