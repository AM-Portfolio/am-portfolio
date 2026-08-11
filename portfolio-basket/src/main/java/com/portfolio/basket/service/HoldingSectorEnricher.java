package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fills blank/Unknown sector (and mcap) on user holdings via the same security-match path as ETF enrichment.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingSectorEnricher {

    private final EtfApiClient etfApiClient;

    public List<EquityHoldings> enrich(List<EquityHoldings> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return holdings == null ? List.of() : holdings;
        }

        List<EquityHoldings> needsEnrichment = holdings.stream()
                .filter(h -> SectorNormalizer.isUnknown(h.getSector()) || h.getMarketCapCategory() == null)
                .filter(h -> (h.getIsin() != null && !h.getIsin().isBlank())
                        || (h.getSymbol() != null && !h.getSymbol().isBlank()))
                .collect(Collectors.toList());

        if (needsEnrichment.isEmpty()) {
            normalizeInPlace(holdings);
            return holdings;
        }

        List<String> queries = needsEnrichment.stream()
                .map(h -> h.getIsin() != null && !h.getIsin().isBlank() ? h.getIsin() : h.getSymbol())
                .distinct()
                .collect(Collectors.toList());

        try {
            // Reuse ETF holdings enricher by wrapping as EtfHolding-like via public enrichHoldings API
            List<com.portfolio.basket.model.EtfHolding> stubs = new ArrayList<>();
            for (String q : queries) {
                com.portfolio.basket.model.EtfHolding stub = new com.portfolio.basket.model.EtfHolding();
                if (q != null && q.toUpperCase().startsWith("INE")) {
                    stub.setIsin(q);
                } else {
                    stub.setSymbol(q);
                }
                stub.setSector("Unknown");
                stubs.add(stub);
            }
            etfApiClient.enrichHoldings(stubs);
            Map<String, com.portfolio.basket.model.EtfHolding> byKey = stubs.stream()
                    .collect(Collectors.toMap(
                            h -> h.getIsin() != null ? h.getIsin() : (h.getSymbol() != null ? h.getSymbol() : ""),
                            h -> h,
                            (a, b) -> a));

            for (EquityHoldings h : holdings) {
                String key = h.getIsin() != null && !h.getIsin().isBlank() ? h.getIsin() : h.getSymbol();
                com.portfolio.basket.model.EtfHolding match = key != null ? byKey.get(key) : null;
                if (match == null && h.getSymbol() != null) {
                    match = byKey.get(h.getSymbol());
                }
                if (match != null) {
                    if (SectorNormalizer.isUnknown(h.getSector()) && match.getSector() != null) {
                        h.setSector(match.getSector());
                    }
                    if (h.getMarketCapCategory() == null && match.getMarketCapCategory() != null) {
                        h.setMarketCapCategory(match.getMarketCapCategory());
                    }
                    if (h.getMarketCapValue() == null && match.getMarketCapValue() != null) {
                        h.setMarketCapValue(match.getMarketCapValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Holding sector enrichment failed (fail-open): {}", e.getMessage());
        }

        normalizeInPlace(holdings);
        return holdings;
    }

    private void normalizeInPlace(List<EquityHoldings> holdings) {
        for (EquityHoldings h : holdings) {
            if (h.getSector() != null) {
                // Keep display sector; matching uses SectorNormalizer in engine
            }
        }
    }
}
