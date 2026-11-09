package com.portfolio.basket.service;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.model.OpportunityMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpportunityModeAndEnrichCoverageTest {

    @Test
    void modeFromDefaultsToFull() {
        assertEquals(OpportunityMode.FULL, OpportunityMode.from(null));
        assertEquals(OpportunityMode.FULL, OpportunityMode.from(""));
        assertEquals(OpportunityMode.FULL, OpportunityMode.from("nope"));
        assertEquals(OpportunityMode.DISCOVER, OpportunityMode.from("discover"));
        assertEquals(OpportunityMode.DISCOVER, OpportunityMode.from("DISCOVER"));
        assertTrue(OpportunityMode.DISCOVER.isDiscover());
        assertFalse(OpportunityMode.FULL.isDiscover());
    }

    @Test
    void slimForDiscoverDropsLineItems() {
        BasketOpportunity op = BasketOpportunity.builder()
                .etfSymbol("NIFTYBEES")
                .matchScore(40)
                .composition(List.of(BasketOpportunity.BasketItem.builder().isin("INE002A01018").build()))
                .buyList(List.of(BasketOpportunity.BasketItem.builder().isin("INE002A01018").build()))
                .etfConstituentIsins(List.of("INE002A01018"))
                .sparklineCloses(List.of(100.0, 101.0))
                .build();
        BasketEngineService.slimForDiscover(op);
        assertNull(op.getComposition());
        assertNull(op.getBuyList());
        assertNull(op.getEtfConstituentIsins());
        assertEquals(2, op.getSparklineCloses().size());
        assertEquals("NIFTYBEES", op.getEtfSymbol());
    }

    @Test
    void isinCoverageThreshold() {
        EtfHolding ok = new EtfHolding();
        ok.setIsin("INE002A01018");
        EtfHolding bad = new EtfHolding();
        bad.setIsin("-");
        assertEquals(1.0, EnrichedEtfService.isinCoverage(List.of(ok)), 0.001);
        assertTrue(EnrichedEtfService.isinCoverage(List.of(ok, ok, ok, ok, bad)) >= 0.8);
        assertTrue(EnrichedEtfService.isinCoverage(List.of(ok, ok, ok, ok, ok, ok, ok, ok, ok, ok,
                ok, ok, ok, ok, ok, ok, ok, ok, ok, bad)) >= 0.95);
    }
}
