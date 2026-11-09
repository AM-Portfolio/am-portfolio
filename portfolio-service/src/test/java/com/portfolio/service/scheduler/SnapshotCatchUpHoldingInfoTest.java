package com.portfolio.service.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SnapshotCatchUpHoldingInfoTest {

    @Test
    void activeOnIncludesEntryAndExitInclusive() {
        var h = new SnapshotCatchUpService.HoldingInfo(
                "RELIANCE", 10, 100, "ZERODHA", "Zerodha",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10));
        assertFalse(h.isActiveOn(LocalDate.of(2026, 1, 9)));
        assertTrue(h.isActiveOn(LocalDate.of(2026, 1, 10)));
        assertTrue(h.isActiveOn(LocalDate.of(2026, 2, 10)));
        assertFalse(h.isActiveOn(LocalDate.of(2026, 2, 11)));
    }

    @Test
    void openPositionHasNoExit() {
        var h = new SnapshotCatchUpService.HoldingInfo(
                "TCS", 5, 50, "ZERODHA", "Zerodha",
                LocalDate.of(2026, 3, 1), null);
        assertFalse(h.isActiveOn(LocalDate.of(2026, 2, 28)));
        assertTrue(h.isActiveOn(LocalDate.of(2026, 9, 21)));
    }

    @Test
    void noDatesMeansAlwaysActive() {
        var h = new SnapshotCatchUpService.HoldingInfo(
                "INFY", 1, 10, "ZERODHA", "Zerodha", null, null);
        assertTrue(h.isActiveOn(LocalDate.of(2020, 1, 1)));
    }
}
