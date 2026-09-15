package com.portfolio.marketdata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portfolio.marketdata.client.MarketCalendarClient;
import com.portfolio.marketdata.model.calendar.MarketCalendarStatus;
import com.portfolio.marketdata.model.calendar.MarketSessionTiming;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DefaultCashSessionClockTest {

    @Mock
    private MarketCalendarClient calendarClient;

    private DefaultCashSessionClock clock;

    @BeforeEach
    void setUp() {
        clock = new DefaultCashSessionClock(calendarClient);
    }

    @Test
    void openStatus_isCashOpen() {
        when(calendarClient.getStatus("NSE")).thenReturn(Mono.just(MarketCalendarStatus.builder()
                .exchange("NSE")
                .open(true)
                .reason("OPEN")
                .sessionStart(LocalTime.of(9, 15))
                .sessionEnd(LocalTime.of(15, 30))
                .build()));

        assertTrue(clock.isCashOpen());
        assertEquals("OPEN", clock.reason());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), clock.sessionDate());
    }

    @Test
    void weekend_failUsesClosed() {
        when(calendarClient.getStatus("NSE")).thenReturn(Mono.just(MarketCalendarStatus.builder()
                .open(false)
                .reason("WEEKEND")
                .build()));
        when(calendarClient.getTimings(eq("NSE"), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    boolean open = d.getDayOfWeek().getValue() < 6;
                    return Mono.just(MarketSessionTiming.builder().date(d).open(open).build());
                });

        assertFalse(clock.isCashOpen());
        assertEquals("WEEKEND", clock.reason());
        LocalDate session = clock.sessionDate();
        assertTrue(session.getDayOfWeek().getValue() < 6);
    }

    @Test
    void calendarError_failClosed() {
        when(calendarClient.getStatus(anyString())).thenReturn(Mono.error(new RuntimeException("timeout")));

        assertFalse(clock.isCashOpen());
        assertEquals("CALENDAR_UNAVAILABLE", clock.reason());
    }
}
