package com.portfolio.analytics.intelligence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioIntelligenceSnapshotFactoryTest {

    @Test
    void usableMeta_rejectsBlankDashAndUnknown() {
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta(null));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta(""));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("  "));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("-"));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("Unknown"));
        assertNull(PortfolioIntelligenceSnapshotFactory.usableMeta("unknown"));
        assertEquals("Financial Services",
                PortfolioIntelligenceSnapshotFactory.usableMeta(" Financial Services "));
    }
}
