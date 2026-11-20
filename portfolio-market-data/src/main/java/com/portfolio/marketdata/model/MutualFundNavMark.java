package com.portfolio.marketdata.model;

import java.time.LocalDate;

/** Latest and prior published end-of-day NAV for one mutual-fund scheme. */
public record MutualFundNavMark(
        String isin,
        String schemeName,
        double nav,
        Double previousNav,
        LocalDate navDate) {
}
