package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.IntelligenceSuggestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntelligenceSuggestServiceTest {

    private final IntelligenceSuggestService service = new IntelligenceSuggestService();

    @Test
    void stressSector_prefersBookSectorsThenCanonical() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                holding("TCS", 50, "Information Technology"),
                holding("INFY", 30, "Information Technology"),
                holding("HDFC", 20, "Financial Services"));

        IntelligenceSuggestResponse res = service.suggest(
                snap, IntelligenceSuggestService.CTX_STRESS_SECTOR, "it", null, 8);

        assertFalse(res.getSuggestions().isEmpty());
        List<String> labels = res.getSuggestions().stream()
                .map(IntelligenceSuggestResponse.SuggestionDto::getLabel)
                .toList();
        assertTrue(labels.contains("Information Technology") || labels.contains("IT"));
        assertTrue(labels.stream().anyMatch(l -> l.toLowerCase().contains("it")
                || l.toLowerCase().contains("technology")));
    }

    @Test
    void whatIfSector_onlyBookLabels() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                holding("TCS", 100, "Information Technology"));

        IntelligenceSuggestResponse miss = service.suggest(
                snap, IntelligenceSuggestService.CTX_WHAT_IF_SECTOR, "zzz", null, 8);
        assertTrue(miss.getSuggestions().isEmpty());

        IntelligenceSuggestResponse hit = service.suggest(
                snap, IntelligenceSuggestService.CTX_WHAT_IF_SECTOR, "info", null, 8);
        assertEquals(1, hit.getSuggestions().size());
        assertEquals("Information Technology", hit.getSuggestions().get(0).getLabel());
    }

    @Test
    void whatIfSymbol_returnsHoldings() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                holding("TCS", 60, "IT"),
                holding("TITAN", 40, "Consumer"));

        IntelligenceSuggestResponse res = service.suggest(
                snap, IntelligenceSuggestService.CTX_WHAT_IF_SYMBOL, "t", null, 8);

        assertEquals(List.of("TCS", "TITAN"),
                res.getSuggestions().stream().map(IntelligenceSuggestResponse.SuggestionDto::getLabel).toList());
    }

    @Test
    void classAdd_usesWireTemplates() {
        PortfolioIntelligenceSnapshot snap = snapshot(holding("TCS", 100, "IT"));

        IntelligenceSuggestResponse res = service.suggest(
                snap, IntelligenceSuggestService.CTX_CLASS_ADD_NAME, "gold", "commodities", 8);

        assertTrue(res.getSuggestions().stream()
                .anyMatch(s -> s.getLabel().toLowerCase().contains("gold")));
        assertTrue(res.getSuggestions().stream()
                .allMatch(s -> "CLASS_TEMPLATE".equals(s.getSource()) || "HOLDING".equals(s.getSource())));
    }

    @Test
    void unknownContext_returnsEmpty() {
        PortfolioIntelligenceSnapshot snap = snapshot(holding("TCS", 100, "IT"));
        IntelligenceSuggestResponse res = service.suggest(snap, "NOPE", "t", null, 8);
        assertTrue(res.getSuggestions().isEmpty());
    }

    private static PortfolioIntelligenceSnapshot snapshot(
            PortfolioIntelligenceSnapshot.Holding... holdings) {
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holdings))
                .totalValue(100)
                .holdingsCount(holdings.length)
                .build();
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol, double weightPct, String sector) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(weightPct)
                .weightPct(weightPct)
                .sector(sector)
                .assetClass(HealthScoreEngine.ASSET_EQUITY)
                .build();
    }
}
