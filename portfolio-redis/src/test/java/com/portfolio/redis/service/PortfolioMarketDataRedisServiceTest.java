package com.portfolio.redis.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioMarketDataRedisServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void isCashMarketHours_weekdaySession() {
        ZonedDateTime fridayNoon = LocalDate.of(2026, 8, 14).atTime(LocalTime.of(12, 0)).atZone(IST);
        assertTrue(PortfolioMarketDataRedisService.isCashMarketHours(fridayNoon));
    }

    @Test
    void isCashMarketHours_afterCloseAndWeekend() {
        ZonedDateTime fridayAfterClose = LocalDate.of(2026, 8, 14).atTime(LocalTime.of(15, 30)).atZone(IST);
        ZonedDateTime saturday = LocalDate.of(2026, 8, 15).atTime(LocalTime.of(13, 0)).atZone(IST);
        assertFalse(PortfolioMarketDataRedisService.isCashMarketHours(fridayAfterClose));
        assertFalse(PortfolioMarketDataRedisService.isCashMarketHours(saturday));
        assertFalse(PortfolioMarketDataRedisService.isCashMarketHours(null));
    }
}
