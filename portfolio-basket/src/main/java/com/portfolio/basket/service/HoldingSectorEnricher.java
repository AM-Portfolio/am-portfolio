package com.portfolio.basket.service;

import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.marketdata.model.BatchSearchResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fills blank/Unknown sector (and mcap) on user holdings via the exact market-data batch search by symbol.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingSectorEnricher {

    private final MarketDataService marketDataService;

    public List<EquityHoldings> enrich(List<EquityHoldings> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return holdings == null ? List.of() : holdings;
        }

        List<EquityHoldings> needsEnrichment = holdings.stream()
                .filter(h -> SectorNormalizer.isUnknown(h.getSector()) || h.getMarketCapCategory() == null)
                .filter(h -> h.getSymbol() != null && !h.getSymbol().isBlank())
                .collect(Collectors.toList());

        if (needsEnrichment.isEmpty()) {
            return holdings;
        }

        List<String> symbols = needsEnrichment.stream()
                .map(EquityHoldings::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        try {
            Map<String, BatchSearchResponse.SecurityMatch> marketCapData =
                    marketDataService.getMarketCapData(symbols);

            for (EquityHoldings h : needsEnrichment) {
                BatchSearchResponse.SecurityMatch match = marketCapData.get(h.getSymbol());
                if (match != null) {
                    if (SectorNormalizer.isUnknown(h.getSector())) {
                        String bestSector = match.getSector();
                        if (bestSector != null && "Financial Services".equalsIgnoreCase(bestSector.trim())
                                && match.getIndustry() != null && !match.getIndustry().isBlank()) {
                            bestSector = match.getIndustry();
                        } else if (bestSector == null && match.getIndustry() != null) {
                            bestSector = match.getIndustry();
                        }
                        if (bestSector != null) {
                            h.setSector(bestSector);
                        }
                    }
                    if (h.getMarketCapCategory() == null && match.getMarketCapType() != null) {
                        h.setMarketCapCategory(match.getMarketCapType());
                    }
                    if (h.getMarketCapValue() == null && match.getMarketCapValue() != null) {
                        h.setMarketCapValue(match.getMarketCapValue().doubleValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Holding sector enrichment via market data failed (fail-open): {}", e.getMessage());
        }

        return holdings;
    }
}
