package com.portfolio.builder;

import com.portfolio.model.StockPerformance;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PerformanceMetrics;
import com.portfolio.service.StockPerformanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisBuilderTest {

    @Mock
    private StockPerformanceService stockPerformanceService;

    @InjectMocks
    private PortfolioAnalysisBuilder portfolioAnalysisBuilder;

    @Test
    void intervalsForMetrics_overall_skipsSubDayWindows() {
        List<TimeInterval> intervals = PortfolioAnalysisBuilder.intervalsForMetrics(TimeInterval.OVERALL);
        assertEquals(6, intervals.size());
        assertTrue(intervals.contains(TimeInterval.ONE_DAY));
        assertTrue(intervals.contains(TimeInterval.ONE_YEAR));
        assertTrue(intervals.stream().noneMatch(i -> i.getDuration() != null && i.getDuration().toHours() < 24));
    }

    @Test
    void intervalsForMetrics_specificInterval_onlyThatOne() {
        List<TimeInterval> intervals = PortfolioAnalysisBuilder.intervalsForMetrics(TimeInterval.ONE_MONTH);
        assertEquals(List.of(TimeInterval.ONE_MONTH), intervals);
    }

    @Test
    void buildTimeBasedMetrics_overall_callsHistoricalValueOncePerCuratedInterval() {
        when(stockPerformanceService.calculateCurrentValue(anyList())).thenReturn(100_000.0);
        when(stockPerformanceService.calculateHistoricalValue(anyList(), any())).thenReturn(90_000.0);

        List<StockPerformance> performances = List.of(StockPerformance.builder().build());
        Map<TimeInterval, PerformanceMetrics> metrics =
                portfolioAnalysisBuilder.buildTimeBasedMetrics(performances, TimeInterval.OVERALL);

        assertEquals(6, metrics.size());
        verify(stockPerformanceService, times(1)).calculateCurrentValue(performances);
        verify(stockPerformanceService, times(6)).calculateHistoricalValue(anyList(), any());
    }
}
