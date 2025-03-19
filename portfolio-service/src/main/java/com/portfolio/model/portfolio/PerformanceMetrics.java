package com.portfolio.model.portfolio;

import java.time.Instant;

import com.portfolio.model.TimeInterval;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceMetrics {
    private TimeInterval interval;
    private double startValue;
    private double endValue;
    private double valueChange;
    private double percentageChange;
    private double volatility;
    private double sharpeRatio;
    private double maxDrawdown;
    private double recoveryTime;
    private Instant startTime;
    private Instant endTime;
}
