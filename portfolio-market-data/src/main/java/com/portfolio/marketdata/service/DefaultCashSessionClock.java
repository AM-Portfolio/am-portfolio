package com.portfolio.marketdata.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import com.portfolio.marketdata.client.MarketCalendarClient;
import com.portfolio.marketdata.model.calendar.MarketCalendarStatus;
import com.portfolio.marketdata.model.calendar.MarketSessionTiming;
import com.portfolio.redis.session.CashSessionClock;

import lombok.extern.slf4j.Slf4j;

/**
 * Single NSE cash session clock: calendar status (60s cache), fail-closed on errors.
 */
@Slf4j
public class DefaultCashSessionClock implements CashSessionClock {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String EXCHANGE = "NSE";
    private static final Duration STATUS_TTL = Duration.ofSeconds(60);
    private static final Duration STATUS_NEGATIVE_TTL = Duration.ofSeconds(15);
    private static final int MAX_LOOKBACK_DAYS = 14;

    private final MarketCalendarClient calendarClient;
    private final AtomicReference<CachedStatus> statusCache = new AtomicReference<>();

    public DefaultCashSessionClock(MarketCalendarClient calendarClient) {
        this.calendarClient = calendarClient;
    }

    @Override
    public boolean isCashOpen() {
        return resolveStatus().open;
    }

    @Override
    public String reason() {
        return resolveStatus().reason;
    }

    @Override
    public LocalDate sessionDate() {
        CachedStatus status = resolveStatus();
        LocalDate today = LocalDate.now(IST);
        if (status.open) {
            return today;
        }
        // Avoid timings fan-out when calendar HTTP already failed (each call burns the timeout).
        if ("CALENDAR_UNAVAILABLE".equals(status.reason)) {
            return weekdayFallback(today.minusDays(1));
        }
        return findLastOpenSessionOnOrBefore(today.minusDays(1));
    }

    @Override
    public LocalDate priorSessionDate() {
        CachedStatus status = resolveStatus();
        if ("CALENDAR_UNAVAILABLE".equals(status.reason)) {
            return weekdayFallback(sessionDate().minusDays(1));
        }
        return findLastOpenSessionOnOrBefore(sessionDate().minusDays(1));
    }

    private CachedStatus resolveStatus() {
        CachedStatus cached = statusCache.get();
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached;
        }
        try {
            MarketCalendarStatus remote = calendarClient.getStatus(EXCHANGE).block();
            if (remote == null) {
                return cacheFailClosed(now);
            }
            CachedStatus fresh = new CachedStatus(
                    remote.isOpen(),
                    remote.getReason() != null ? remote.getReason() : (remote.isOpen() ? "OPEN" : "OUTSIDE_SESSION"),
                    now.plus(STATUS_TTL));
            statusCache.set(fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("[CashSessionClock] calendar status unavailable — fail-closed: {}", e.getMessage());
            return cacheFailClosed(now);
        }
    }

    private CachedStatus cacheFailClosed(Instant now) {
        CachedStatus closed = new CachedStatus(false, "CALENDAR_UNAVAILABLE", now.plus(STATUS_NEGATIVE_TTL));
        statusCache.set(closed);
        return closed;
    }

    private LocalDate findLastOpenSessionOnOrBefore(LocalDate start) {
        LocalDate cursor = start;
        for (int i = 0; i < MAX_LOOKBACK_DAYS; i++) {
            if (isSessionOpenDay(cursor)) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return weekdayFallback(start);
    }

    private boolean isSessionOpenDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        try {
            MarketSessionTiming timing = calendarClient.getTimings(EXCHANGE, date).block();
            if (timing == null) {
                return true; // weekday with no timing payload → treat as open day
            }
            return timing.isOpen();
        } catch (Exception e) {
            log.debug("[CashSessionClock] timings miss date={}: {}", date, e.getMessage());
            return true;
        }
    }

    private static LocalDate weekdayFallback(LocalDate start) {
        LocalDate cursor = start;
        for (int i = 0; i < MAX_LOOKBACK_DAYS; i++) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return start;
    }

    private record CachedStatus(boolean open, String reason, Instant expiresAt) {}
}
