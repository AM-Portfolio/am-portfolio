package com.portfolio.model;

import java.time.Duration;

import lombok.Getter;

@Getter
public enum TimeInterval {
    FIVE_MINUTES(Duration.ofMinutes(5), "5m"),
    TEN_MINUTES(Duration.ofMinutes(10), "10m"),
    FIFTEEN_MINUTES(Duration.ofMinutes(15), "15m"),
    THIRTY_MINUTES(Duration.ofMinutes(30), "30m"),
    ONE_HOUR(Duration.ofHours(1), "1H"),
    ONE_DAY(Duration.ofDays(1), "1D"),
    ONE_WEEK(Duration.ofDays(7), "1W"),
    ONE_MONTH(Duration.ofDays(30), "1M"),
    ONE_YEAR(Duration.ofDays(365), "1Y"),
    OVERALL(null, "all");

    private final Duration duration;
    private final String code;

    TimeInterval(Duration duration, String code) {
        this.duration = duration;
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public Duration getDuration() {
        return duration;
    }

    public static TimeInterval fromCode(String code) {
        if (code == null) return OVERALL;
        
        for (TimeInterval interval : values()) {
            if (interval.getCode().equalsIgnoreCase(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Invalid time interval code: " + code);
    }
}
