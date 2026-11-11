package com.portfolio.service.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.portfolio.model.portfolio.IntradayDataPoint;

class PortfolioIntradayCliffGuardTest {

    @Test
    void guardAbsurdSessionCliff_reanchorsWhenOpenIsInflated() {
        List<IntradayDataPoint> cliff = List.of(
                IntradayDataPoint.builder().timestamp("09:15").totalWealth(10000).changeFromOpen(0).changeFromOpenPct(0).build(),
                IntradayDataPoint.builder().timestamp("09:20").totalWealth(2400).changeFromOpen(-7600).changeFromOpenPct(-76).build(),
                IntradayDataPoint.builder().timestamp("15:15").totalWealth(2392).changeFromOpen(-7608).changeFromOpenPct(-76.08).build());

        List<IntradayDataPoint> fixed = PortfolioIntradayService.guardAbsurdSessionCliff(cliff);
        assertEquals(3, fixed.size());
        double openPct = fixed.get(0).getChangeFromOpenPct();
        double closePct = fixed.get(fixed.size() - 1).getChangeFromOpenPct();
        assertTrue(Math.abs(closePct) <= 25.0, "close pct was " + closePct);
        assertTrue(Math.abs(openPct) <= 25.0, "open pct was " + openPct);
    }

    @Test
    void guardAbsurdSessionCliff_leavesNormalDayAlone() {
        List<IntradayDataPoint> normal = List.of(
                IntradayDataPoint.builder().timestamp("09:15").totalWealth(10000).build(),
                IntradayDataPoint.builder().timestamp("15:15").totalWealth(10100).build());
        List<IntradayDataPoint> out = PortfolioIntradayService.guardAbsurdSessionCliff(normal);
        assertEquals(10000, out.get(0).getTotalWealth(), 0.01);
        assertEquals(0.0, out.get(0).getChangeFromOpenPct(), 0.01);
    }
}
