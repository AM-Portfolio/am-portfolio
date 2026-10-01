package com.portfolio.service.portfolio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioIntradayServiceSessionTest {

    @Test
    void resolveSessionDate_weekend_rollsToFriday() {
        LocalDate sunday = LocalDate.of(2026, 9, 20); // Sunday
        LocalDate session = PortfolioIntradayService.resolveSessionDate(
                sunday, LocalTime.of(12, 0), false);
        assertEquals(LocalDate.of(2026, 9, 18), session); // Friday
    }

    @Test
    void resolveSessionDate_preOpenWeekday_rollsBack() {
        LocalDate monday = LocalDate.of(2026, 9, 21);
        LocalDate session = PortfolioIntradayService.resolveSessionDate(
                monday, LocalTime.of(8, 0), false);
        assertEquals(LocalDate.of(2026, 9, 18), session); // prior Friday
    }

    @Test
    void resolveSessionDate_midweekHoliday_walksToPriorWeekday() {
        LocalDate wednesday = LocalDate.of(2026, 9, 16);
        LocalDate session = PortfolioIntradayService.resolveSessionDate(
                wednesday, LocalTime.of(11, 0), false);
        assertEquals(LocalDate.of(2026, 9, 15), session);
    }

    @Test
    void resolveSessionDate_openSession_staysToday() {
        LocalDate day = LocalDate.of(2026, 9, 21);
        LocalDate session = PortfolioIntradayService.resolveSessionDate(
                day, LocalTime.of(14, 0), true);
        assertEquals(day, session);
    }

    @Test
    void fillSessionGrid_partialAfternoonCandles_carryForwardAfterFirstBar() {
        TreeMap<LocalTime, Map<String, Double>> candles = new TreeMap<>();
        candles.put(LocalTime.of(13, 55), Map.of("RELIANCE", 1400.0));
        candles.put(LocalTime.of(14, 0), Map.of("RELIANCE", 1401.0));

        TreeMap<LocalTime, Map<String, Double>> filled = PortfolioIntradayService.fillSessionGrid(
                candles, Map.of(), LocalTime.of(15, 30));

        assertFalse(filled.containsKey(LocalTime.of(9, 20)),
                "must not invent morning prices from afternoon candles");
        assertTrue(filled.containsKey(LocalTime.of(13, 55)));
        assertEquals(1400.0, filled.get(LocalTime.of(13, 55)).get("RELIANCE"));
        assertTrue(filled.containsKey(LocalTime.of(14, 5)));
        assertEquals(1401.0, filled.get(LocalTime.of(14, 5)).get("RELIANCE"));
        assertTrue(filled.containsKey(LocalTime.of(15, 30)));
    }

    @Test
    void fillSessionGrid_emptyCandles_usesFlatSeedFullSession() {
        Map<String, Double> seed = new HashMap<>();
        seed.put("RELIANCE", 100.0);
        TreeMap<LocalTime, Map<String, Double>> filled = PortfolioIntradayService.fillSessionGrid(
                new TreeMap<>(), seed, LocalTime.of(15, 30));

        assertTrue(filled.containsKey(LocalTime.of(9, 15)));
        assertTrue(filled.containsKey(LocalTime.of(15, 30)));
        assertEquals(100.0, filled.get(LocalTime.of(12, 0)).get("RELIANCE"));
    }

    @Test
    void resolveSessionEnd_liveDay_capsAtNow() {
        LocalTime end = PortfolioIntradayService.resolveSessionEnd(
                LocalTime.of(11, 42, 33),
                true,
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 21));
        assertEquals(LocalTime.of(11, 42), end);
    }

    @Test
    void resolveSessionEnd_priorSession_fullClose() {
        LocalTime end = PortfolioIntradayService.resolveSessionEnd(
                LocalTime.of(10, 0),
                false,
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 21));
        assertEquals(LocalTime.of(15, 30), end);
    }
}
