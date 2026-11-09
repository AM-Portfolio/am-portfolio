package com.portfolio.redis.session;

import java.time.LocalDate;

/**
 * NSE cash session gate. Implemented by market-data {@code CashSessionClock}
 * (calendar-backed). Callers in redis/kafka/service should prefer this over local Mon–Fri clocks.
 */
public interface CashSessionClock {

    /** True when the cash market is open right now (calendar + session window). */
    boolean isCashOpen();

    /** Reason from calendar status, e.g. OPEN, WEEKEND, HOLIDAY, OUTSIDE_SESSION, CALENDAR_UNAVAILABLE. */
    String reason();

    /**
     * Trading date for prices being shown: today when open; last completed NSE session when closed.
     */
    LocalDate sessionDate();

    /**
     * Session immediately before {@link #sessionDate()} (for day-% reference when closed).
     * Walks calendar timings; weekday fallback if calendar unavailable.
     */
    LocalDate priorSessionDate();
}
