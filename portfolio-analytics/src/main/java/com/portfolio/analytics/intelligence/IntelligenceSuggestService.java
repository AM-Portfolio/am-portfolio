package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.IntelligenceSuggestResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Context-aware suggestions for Stress / What-If / Class Add — not a shared UI catalog.
 */
@Service
public class IntelligenceSuggestService {

    public static final String CTX_STRESS_SECTOR = "STRESS_SECTOR";
    public static final String CTX_WHAT_IF_SYMBOL = "WHAT_IF_SYMBOL";
    public static final String CTX_WHAT_IF_SECTOR = "WHAT_IF_SECTOR";
    public static final String CTX_CLASS_ADD_NAME = "CLASS_ADD_NAME";

    /** Canonical stress labels (wire values StressEngine understands via {@link SectorMatcher}). */
    private static final List<String> STRESS_CANONICAL = List.of(
            "Banking",
            "IT",
            "Information Technology",
            "Auto",
            "Automobiles",
            "Pharma",
            "Pharmaceuticals",
            "Energy",
            "Financial Services",
            "Healthcare");

    private static final Map<String, List<String>> CLASS_TEMPLATES = Map.of(
            "bonds", List.of(
                    "Sovereign Gold Bond", "Government Bond", "Corporate Bond",
                    "Treasury Bill", "Debt Mutual Fund"),
            "commodities", List.of(
                    "Sovereign Gold", "Gold ETF", "Silver ETF", "Commodity Fund"),
            "cash", List.of(
                    "Savings Account", "Liquid Fund", "Overnight Fund", "Cash Balance"));

    public IntelligenceSuggestResponse suggest(
            PortfolioIntelligenceSnapshot snapshot,
            String context,
            String query,
            String wire,
            int limit) {
        String ctx = context == null ? "" : context.trim().toUpperCase(Locale.ROOT);
        String q = query == null ? "" : query.trim();
        int lim = limit > 0 && limit <= 20 ? limit : 8;

        List<IntelligenceSuggestResponse.SuggestionDto> items = switch (ctx) {
            case CTX_STRESS_SECTOR -> suggestStressSectors(snapshot, q, lim);
            case CTX_WHAT_IF_SECTOR -> suggestWhatIfSectors(snapshot, q, lim);
            case CTX_WHAT_IF_SYMBOL -> suggestWhatIfSymbols(snapshot, q, lim);
            case CTX_CLASS_ADD_NAME -> suggestClassAddNames(snapshot, q, wire, lim);
            default -> List.of();
        };

        return IntelligenceSuggestResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .context(ctx)
                .query(q)
                .suggestions(items)
                .build();
    }

    private List<IntelligenceSuggestResponse.SuggestionDto> suggestStressSectors(
            PortfolioIntelligenceSnapshot snapshot, String q, int limit) {
        Map<String, Double> weightBySector = sectorWeights(snapshot);
        List<IntelligenceSuggestResponse.SuggestionDto> preferred = new ArrayList<>();
        for (Map.Entry<String, Double> e : weightBySector.entrySet()) {
            if (!matchesQuery(e.getKey(), q)) {
                continue;
            }
            int matched = countMatchedHoldings(snapshot, e.getKey());
            preferred.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                    .label(e.getKey())
                    .subtitle(String.format(Locale.ROOT, "%.1f%% of book · %d holdings", e.getValue(), matched))
                    .source("PORTFOLIO_SECTOR")
                    .weightPct(PortfolioIntelligenceSnapshotFactory.round2(e.getValue()))
                    .matchedHoldings(matched)
                    .build());
        }
        preferred.sort(Comparator.comparingDouble(
                (IntelligenceSuggestResponse.SuggestionDto s) -> s.getWeightPct() == null ? 0 : s.getWeightPct())
                .reversed());

        List<IntelligenceSuggestResponse.SuggestionDto> canonical = new ArrayList<>();
        for (String label : STRESS_CANONICAL) {
            if (!matchesQuery(label, q)) {
                continue;
            }
            int matched = countMatchedHoldings(snapshot, label);
            if (matched <= 0 && !q.isEmpty()) {
                // Still offer canonical when query matches — user may want 0%-impact honesty.
            }
            canonical.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                    .label(label)
                    .subtitle(matched > 0
                            ? matched + " holdings match"
                            : "Canonical stress sector")
                    .source("CANONICAL")
                    .matchedHoldings(matched)
                    .build());
        }

        return mergeSuggestions(preferred, canonical, limit);
    }

    private List<IntelligenceSuggestResponse.SuggestionDto> suggestWhatIfSectors(
            PortfolioIntelligenceSnapshot snapshot, String q, int limit) {
        // Only portfolio sectors (exact holding labels) — SWITCH needs labels that exist on book.
        Map<String, Double> weightBySector = sectorWeights(snapshot);
        List<IntelligenceSuggestResponse.SuggestionDto> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : weightBySector.entrySet()) {
            if (!matchesQuery(e.getKey(), q)) {
                continue;
            }
            out.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                    .label(e.getKey())
                    .subtitle(String.format(Locale.ROOT, "%.1f%% of book", e.getValue()))
                    .source("PORTFOLIO_SECTOR")
                    .weightPct(PortfolioIntelligenceSnapshotFactory.round2(e.getValue()))
                    .build());
        }
        out.sort(Comparator.comparingDouble(
                (IntelligenceSuggestResponse.SuggestionDto s) -> s.getWeightPct() == null ? 0 : s.getWeightPct())
                .reversed());
        if (out.size() > limit) {
            return out.subList(0, limit);
        }
        return out;
    }

    private List<IntelligenceSuggestResponse.SuggestionDto> suggestWhatIfSymbols(
            PortfolioIntelligenceSnapshot snapshot, String q, int limit) {
        List<IntelligenceSuggestResponse.SuggestionDto> out = new ArrayList<>();
        if (snapshot.getHoldings() == null) {
            return out;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (h.getSymbol() == null || h.getSymbol().isBlank()) {
                continue;
            }
            String sym = h.getSymbol().trim();
            String name = sym;
            if (!matchesQuery(sym, q) && !matchesQuery(name, q)) {
                continue;
            }
            out.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                    .label(sym)
                    .symbol(sym)
                    .subtitle(String.format(Locale.ROOT, "Held · %.1f%%", h.getWeightPct()))
                    .source("HOLDING")
                    .weightPct(PortfolioIntelligenceSnapshotFactory.round2(h.getWeightPct()))
                    .build());
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<IntelligenceSuggestResponse.SuggestionDto> suggestClassAddNames(
            PortfolioIntelligenceSnapshot snapshot, String q, String wire, int limit) {
        String w = wire == null ? "" : wire.trim().toLowerCase(Locale.ROOT);
        List<IntelligenceSuggestResponse.SuggestionDto> preferred = new ArrayList<>();
        if (snapshot.getHoldings() != null) {
            for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
                String cls = HealthScoreEngine.normalizeAssetClass(h.getAssetClass());
                if (!classWireMatches(w, cls)) {
                    continue;
                }
                String label = h.getSymbol() != null ? h.getSymbol() : "";
                if (label.isBlank() || !matchesQuery(label, q)) {
                    continue;
                }
                preferred.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                        .label(label)
                        .subtitle("On book")
                        .source("HOLDING")
                        .build());
            }
        }
        List<IntelligenceSuggestResponse.SuggestionDto> templates = new ArrayList<>();
        for (String name : CLASS_TEMPLATES.getOrDefault(w, List.of())) {
            if (!matchesQuery(name, q)) {
                continue;
            }
            templates.add(IntelligenceSuggestResponse.SuggestionDto.builder()
                    .label(name)
                    .subtitle("Template")
                    .source("CLASS_TEMPLATE")
                    .build());
        }
        return mergeSuggestions(preferred, templates, limit);
    }

    private static boolean classWireMatches(String wire, String normalizedClass) {
        return switch (wire) {
            case "bonds" -> HealthScoreEngine.ASSET_FIXED_INCOME.equals(normalizedClass);
            case "commodities" -> HealthScoreEngine.ASSET_COMMODITY.equals(normalizedClass);
            case "cash" -> HealthScoreEngine.ASSET_CASH.equals(normalizedClass);
            default -> false;
        };
    }

    private Map<String, Double> sectorWeights(PortfolioIntelligenceSnapshot snapshot) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (snapshot.getHoldings() == null) {
            return map;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            String sector = h.getSector();
            if (SectorMatcher.isUnusable(sector)) {
                continue;
            }
            map.merge(sector.trim(), h.getWeightPct(), Double::sum);
        }
        return map;
    }

    int countMatchedHoldings(PortfolioIntelligenceSnapshot snapshot, String sector) {
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        int n = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (SectorMatcher.matches(h.getSector(), sector)) {
                n++;
            }
        }
        return n;
    }

    double matchedWeightPct(PortfolioIntelligenceSnapshot snapshot, String sector) {
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        double w = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (SectorMatcher.matches(h.getSector(), sector)) {
                w += h.getWeightPct();
            }
        }
        return PortfolioIntelligenceSnapshotFactory.round2(w);
    }

    private static boolean matchesQuery(String label, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static List<IntelligenceSuggestResponse.SuggestionDto> mergeSuggestions(
            List<IntelligenceSuggestResponse.SuggestionDto> preferred,
            List<IntelligenceSuggestResponse.SuggestionDto> rest,
            int limit) {
        List<IntelligenceSuggestResponse.SuggestionDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (IntelligenceSuggestResponse.SuggestionDto s : preferred) {
            String key = s.getLabel().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            out.add(s);
            if (out.size() >= limit) {
                return out;
            }
        }
        for (IntelligenceSuggestResponse.SuggestionDto s : rest) {
            String key = s.getLabel().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            out.add(s);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }
}
